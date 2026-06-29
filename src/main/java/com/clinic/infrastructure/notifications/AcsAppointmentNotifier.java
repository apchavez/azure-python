package com.clinic.infrastructure.notifications;

import com.azure.communication.email.EmailClient;
import com.azure.communication.email.EmailClientBuilder;
import com.azure.communication.email.models.EmailAddress;
import com.azure.communication.email.models.EmailMessage;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.clinic.domain.entities.Appointment;
import com.clinic.domain.ports.AppointmentNotifier;

import java.util.logging.Logger;

/**
 * Azure Communication Services adapter implementing the notification port.
 * Sends transactional emails to the insured party on appointment lifecycle events.
 *
 * Best-effort: failures are logged but never propagated. A notification failure
 * must not prevent the appointment lifecycle from completing.
 *
 * Authenticates via Managed Identity (DefaultAzureCredential).
 * Skips silently when contactEmail is absent or ACS endpoint is not configured.
 */
public class AcsAppointmentNotifier implements AppointmentNotifier {

    private static final Logger LOG = Logger.getLogger(AcsAppointmentNotifier.class.getName());

    private final EmailClient emailClient;
    private final String senderAddress;

    public AcsAppointmentNotifier(String acsEndpoint, String senderAddress) {
        this.senderAddress = senderAddress;
        this.emailClient = new EmailClientBuilder()
                .endpoint(acsEndpoint)
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient();
    }

    @Override
    public void notifyCompleted(Appointment appointment) {
        if (!hasEmail(appointment)) return;
        send(appointment.getContactEmail(),
                "Your appointment has been confirmed",
                "Your appointment (ID: " + appointment.getAppointmentId()
                        + ", schedule: " + appointment.getScheduleId()
                        + ") has been successfully processed.");
    }

    @Override
    public void notifyCancelled(Appointment appointment) {
        if (!hasEmail(appointment)) return;
        send(appointment.getContactEmail(),
                "Your appointment has been cancelled",
                "Your appointment (ID: " + appointment.getAppointmentId()
                        + ", schedule: " + appointment.getScheduleId()
                        + ") has been cancelled.");
    }

    @Override
    public void notifyRescheduled(Appointment old, Appointment newAppointment) {
        if (!hasEmail(old)) return;
        send(old.getContactEmail(),
                "Your appointment has been rescheduled",
                "Your appointment (ID: " + old.getAppointmentId()
                        + ") has been rescheduled to a new slot (schedule: "
                        + newAppointment.getScheduleId()
                        + "). New appointment ID: " + newAppointment.getAppointmentId() + ".");
    }

    private void send(String to, String subject, String body) {
        try {
            EmailMessage message = new EmailMessage()
                    .setSenderAddress(senderAddress)
                    .setToRecipients(new EmailAddress(to))
                    .setSubject(subject)
                    .setBodyPlainText(body);
            emailClient.beginSend(message).getFinalResult();
            LOG.info("Notification sent to " + to + " — " + subject);
        } catch (Exception e) {
            LOG.warning("Notification failed (best-effort, continuing): " + e.getMessage());
        }
    }

    private boolean hasEmail(Appointment appointment) {
        return appointment.getContactEmail() != null && !appointment.getContactEmail().isBlank();
    }
}
