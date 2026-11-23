package games.orbit.orbitstaff.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Point d'entree du service central OrbitStaff.
 *
 * <p>C'est la seule application autorisee a acceder directement a MariaDB. Le
 * plugin Paper et le bot Discord passent exclusivement par ses endpoints REST,
 * authentifies par cle de service.</p>
 */
@SpringBootApplication
public class OrbitStaffApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrbitStaffApiApplication.class, args);
    }
}
