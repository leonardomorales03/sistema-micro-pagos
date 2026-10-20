package com.micropay.backend.infrastructure.notifiers;

import com.micropay.backend.domain.entities.User;
import com.micropay.backend.domain.ports.out.NotificationAdapter;
import com.micropay.backend.domain.ports.out.UserProfileRepository;
import com.micropay.backend.domain.ports.out.UserRepository;
import com.micropay.backend.domain.valueobjects.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;

/**
 * Adaptador de canal EMAIL (Requisito 25.1, 25.6) para {@link NotificationAdapter}.
 *
 * · Sólo envía si {@code micropay.notifications.email.enabled=true} (por defecto false: dev/tests no envían).
 * · Respeta el opt-out del usuario ({@code user_profiles.email_notif_enabled}).
 * · Sólo incluye campos del payload del evento (nunca datos sensibles completos).
 */
@Component
public class EmailNotificationAdapter {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationAdapter.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final UserRepository users;
    private final UserProfileRepository profiles;

    @Value("${micropay.notifications.email.enabled:false}")
    private boolean enabled;

    @Value("${micropay.notifications.email.from:no-reply@micropay.example}")
    private String from;

    @Value("${micropay.notifications.email.subject-prefix:[MicroPay]}")
    private String subjectPrefix;

    public EmailNotificationAdapter(ObjectProvider<JavaMailSender> mailSender,
                                    UserRepository users,
                                    UserProfileRepository profiles) {
        this.mailSender = mailSender;
        this.users = users;
        this.profiles = profiles;
    }

    /**
     * Envía un email transaccional al usuario. Nunca propaga excepciones: el envío
     * es best-effort y no debe romper la operación de negocio que lo originó.
     */
    public void send(UserId userId, String template, Map<String, Object> payload) {
        if (!enabled || userId == null || template == null) return;
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.debug("JavaMailSender no configurado; se omite email {}", template);
            return;
        }
        try {
            if (profiles.findByUserId(userId).map(p -> !p.emailNotifEnabled()).orElse(false)) {
                log.debug("Usuario {} tiene email_notif_enabled=false; se omite {}", userId, template);
                return;
            }
            String to = users.findById(userId).map(User::email).map(Object::toString).orElse(null);
            if (to == null) {
                log.debug("Usuario {} sin email; se omite notificación {}", userId, template);
                return;
            }

            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(from);
            msg.setTo(to);
            msg.setSubject(subjectPrefix + " " + humanize(template));
            msg.setText(renderBody(template, payload));
            sender.send(msg);

            log.info("[NOTIF_EMAIL] Enviado template={} userId={}", template, userId);
        } catch (Exception e) {
            log.warn("[NOTIF_EMAIL] Fallo enviando template={} userId={}: {}", template, userId, e.getMessage());
        }
    }

    // ──────────────── Render ────────────────

    /** Convierte SUBSCRIPTION_CHARGED → "Subscription charged". */
    static String humanize(String template) {
        String lower = template.replace('_', ' ').toLowerCase();
        return lower.isEmpty() ? template : Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    /**
     * Cuerpo en texto plano con los campos del evento (clave: valor), escapados.
     * Orden alfabético para que el resultado sea determinista.
     */
    static String renderBody(String template, Map<String, Object> payload) {
        StringJoiner j = new StringJoiner("\n");
        j.add(humanize(template));
        if (payload != null && !payload.isEmpty()) {
            new TreeMap<>(payload).forEach((k, v) ->
                    j.add("- " + k + ": " + HtmlUtils.htmlEscape(v == null ? "" : String.valueOf(v))));
        }
        j.add("");
        j.add("Este mensaje es automático, por favor no responda.");
        return j.toString();
    }
}
