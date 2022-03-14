package zhigalin.predictions.model.user;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import javax.persistence.*;

@Builder
@ToString
@EqualsAndHashCode
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@JsonIgnoreProperties({"hibernateLazyInitializer"})
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "User_generator")
    @SequenceGenerator(sequenceName = "User_sequence", name = "User_generator")
    private Long id;

    private String login;

    private String password;

    @OneToOne(fetch = FetchType.LAZY, targetEntity = Role.class, cascade = CascadeType.MERGE)
    @JoinColumn
    private Role role;
}
