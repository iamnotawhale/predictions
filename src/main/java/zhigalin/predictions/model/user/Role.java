package zhigalin.predictions.model.user;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import javax.persistence.*;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "role")
@JsonIgnoreProperties({"hibernateLazyInitializer"})
@Entity
public class Role{
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "Role_generator")
    @SequenceGenerator(sequenceName = "Role_sequence", name = "Role_generator", allocationSize = 1)
    private Long id;
    private String role;
}
