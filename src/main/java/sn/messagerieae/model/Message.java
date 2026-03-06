package sn.messagerieae.model;

import jakarta.persistence.*;
import lombok.*;
import sn.messagerieae.model.enums.StatutMessage;

import java.time.LocalDateTime;

@Entity
@Table(name = "messages")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    @Column(nullable = false, length = 1000)
    private String contenu;

    @Column(name = "date_envoi", nullable = false, updatable = false)
    private LocalDateTime dateEnvoi;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StatutMessage statut = StatutMessage.ENVOYE;

    @PrePersist
    protected void onSend() {
        this.dateEnvoi = LocalDateTime.now();
        if (this.statut == null) {
            this.statut = StatutMessage.ENVOYE;
        }
    }

    // Constructeur pratique pour l'envoi
    public Message(User sender, User receiver, String contenu) {
        this.sender = sender;
        this.receiver = receiver;
        this.contenu = contenu;
    }
}
