package games.orbit.orbitstaff.api.tools;

import games.orbit.orbitstaff.common.util.ServiceKeyGenerator;
import games.orbit.orbitstaff.common.util.ServiceKeyHasher;

/**
 * Outil d'onboarding (hors Spring, sans base) : genere une cle de service et
 * affiche son hash a inserer dans {@code service_accounts}.
 *
 * <p>Usage :</p>
 * <pre>
 *   java -cp orbitstaff-api.jar games.orbit.orbitstaff.api.tools.ServiceKeyTool CORE
 * </pre>
 *
 * <p>La cle en clair n'est affichee qu'une seule fois : elle est a placer dans
 * la variable d'environnement du service (plugin ou bot), jamais versionnee.</p>
 */
public final class ServiceKeyTool {

    private ServiceKeyTool() {
    }

    public static void main(String[] args) {
        String kind = args.length > 0 ? args[0].toUpperCase() : "CORE";
        String rawKey = ServiceKeyGenerator.generate();
        String hash = ServiceKeyHasher.hash(rawKey);

        System.out.println("=== OrbitStaff - nouvelle cle de service (" + kind + ") ===");
        System.out.println();
        System.out.println("Cle en clair (a copier UNE SEULE FOIS dans le .env du service) :");
        System.out.println("  " + rawKey);
        System.out.println();
        System.out.println("Hash a stocker en base (ne revele pas la cle) :");
        System.out.println("  " + hash);
        System.out.println();
        System.out.println("Exemple d'insertion (adapter server_id et name) :");
        System.out.println("  INSERT INTO service_accounts (server_id, name, kind, key_hash, revoked, created_at)");
        System.out.println("  VALUES (1, 'onboarding', '" + kind + "', '" + hash + "', FALSE, UTC_TIMESTAMP(3));");
    }
}
