package fr.orbit.tele;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Son d'un écran. Minecraft ne sait pas jouer un son en direct sans mod : le son passe donc par le navigateur.
 * La page de diffusion l'encode en Opus (paquets de 20 ms) et le dépose ici ; les pages d'écoute des joueurs
 * viennent le chercher par des requêtes qui attendent le paquet suivant. Aucune dépendance au serveur de jeu.
 *
 * <p>Corps d'un dépôt : suite de {@code [u16 longueur][paquet]}.
 * Réponse à une écoute : {@code [u8 version][u32 flux][u32 premier][u8 volume][u8 n][nom][u8 n][diffuseur]}
 * puis la même suite de paquets. Flux 0 = rien à entendre (le volume porte alors la raison).
 */
final class Son {

    /** Raisons d'un silence, à la place du volume quand le flux vaut 0. */
    static final int HORS_JEU = 0, RIEN_A_PORTEE = 1;

    /** 5 secondes de paquets gardés. */
    private static final int TAMPON = 256;
    private static final int MAX_PAQUET = 1500;
    /** Un nouvel auditeur démarre à ce nombre de paquets du direct. */
    private static final int DEPART = 3;
    /** Auditeur en retard de plus que ça : il saute au direct. */
    private static final int RETARD = 75;
    private static final int PAR_REPONSE = 100;
    private static final long SILENCE_MS = 3000;

    private static final AtomicInteger FLUX = new AtomicInteger((int) (System.nanoTime() & 0xFFFFF) + 1);

    private final ReentrantLock verrou = new ReentrantLock();
    private final Condition arrivee = verrou.newCondition();
    private final byte[][] paquets = new byte[TAMPON][];
    private final Map<String, Long> auditeurs = new ConcurrentHashMap<>();
    private long suite;
    private int flux;
    private volatile long dernier;

    /** Du son est arrivé il y a moins de trois secondes. */
    boolean actif(long maintenant) {
        return maintenant - dernier < SILENCE_MS;
    }

    /** Pages d'écoute qui ont demandé la suite il y a moins de quatre secondes. */
    int auditeurs(long maintenant) {
        auditeurs.values().removeIf(t -> maintenant - t > 4000);
        return auditeurs.size();
    }

    boolean ecoute(String nom, long maintenant) {
        Long t = auditeurs.get(nom);
        return t != null && maintenant - t <= 4000;
    }

    void couper() {
        dernier = 0;
    }

    /** Dépôt de la page de diffusion. Renvoie le nombre de paquets pris. */
    int recevoir(byte[] corps, long maintenant) {
        verrou.lock();
        try {
            if (flux == 0 || maintenant - dernier >= SILENCE_MS) {
                // nouvelle diffusion : les pages d'écoute repartent de zéro
                flux = FLUX.incrementAndGet();
                suite = 0;
            }
            int i = 0, n = 0;
            while (i + 2 <= corps.length) {
                int l = ((corps[i] & 255) << 8) | (corps[i + 1] & 255);
                i += 2;
                if (l == 0 || l > MAX_PAQUET || i + l > corps.length) {
                    break;
                }
                paquets[(int) (suite % TAMPON)] = Arrays.copyOfRange(corps, i, i + l);
                suite++;
                n++;
                i += l;
            }
            if (n > 0) {
                dernier = maintenant;
                arrivee.signalAll();
            }
            return n;
        } finally {
            verrou.unlock();
        }
    }

    /**
     * Paquets à partir du numéro {@code voulu} ; attend le prochain au plus {@code attenteMs}.
     *
     * @param fluxClient flux que la page lit déjà (autre chose = elle démarre, on la place près du direct)
     */
    byte[] lire(int fluxClient, long voulu, long attenteMs, int volume, String nom, String diffuseur, String auditeur, long maintenant)
            throws InterruptedException {
        auditeurs.put(auditeur, maintenant);
        verrou.lock();
        try {
            int vu = fluxClient;
            long reste = TimeUnit.MILLISECONDS.toNanos(attenteMs);
            while (true) {
                if (vu != flux || voulu < 0 || voulu > suite || suite - voulu > RETARD) {
                    vu = flux;
                    voulu = Math.max(0, suite - DEPART);
                }
                if (voulu < suite || reste <= 0) {
                    break;
                }
                reste = arrivee.awaitNanos(reste);
            }
            long fin = Math.min(suite, voulu + PAR_REPONSE);
            ByteArrayOutputStream o = entete(flux, voulu, volume, nom, diffuseur);
            for (long k = voulu; k < fin; k++) {
                byte[] p = paquets[(int) (k % TAMPON)];
                o.write(p.length >> 8);
                o.write(p.length);
                o.write(p, 0, p.length);
            }
            return o.toByteArray();
        } finally {
            verrou.unlock();
        }
    }

    /** Réponse « rien à entendre ». */
    static byte[] rien(int raison) {
        return entete(0, 0, raison, "", "").toByteArray();
    }

    private static ByteArrayOutputStream entete(int flux, long premier, int volume, String nom, String diffuseur) {
        ByteArrayOutputStream o = new ByteArrayOutputStream(2048);
        o.write(1);
        entier(o, flux);
        entier(o, (int) premier);
        o.write(Math.max(0, Math.min(255, volume)));
        for (String s : new String[]{nom, diffuseur}) {
            byte[] b = s.getBytes(StandardCharsets.UTF_8);
            int n = Math.min(b.length, 255);
            o.write(n);
            o.write(b, 0, n);
        }
        return o;
    }

    private static void entier(ByteArrayOutputStream o, int v) {
        o.write(v >>> 24);
        o.write(v >>> 16);
        o.write(v >>> 8);
        o.write(v);
    }

    /**
     * Volume selon la distance : plein jusqu'à {@code plein} blocs, puis il baisse jusqu'à zéro à {@code portee}.
     *
     * @return 0 à 255, ou -1 hors de portée
     */
    static int volume(double distance, double plein, double portee) {
        if (distance >= portee) {
            return -1;
        }
        if (distance <= plein) {
            return 255;
        }
        double v = (portee - distance) / (portee - plein);
        return Math.max(1, (int) Math.round(255 * v * v));
    }
}
