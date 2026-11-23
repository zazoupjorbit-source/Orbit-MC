import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Écrit les modèles 3D des pets de l'aile Tycoon (La Station) dans le pack du lobby :
 * pack/assets/orbitdev/items/pet_*.json et models/item/pet_*.json.
 * Modèles en cubes, textures vanilla (béton bleu, bloc d'or, lanterne aquatique...) : rien à dessiner.
 * Usage : java tools/OrbitPetsGen.java pack
 */
public class OrbitPetsGen {

    /** Textures : b bleu glacé, d bleu nuit, o or, w blanc, n noir, l lumière, g verre bleu. */
    private static final String TEXTURES = """
            "b": "minecraft:block/light_blue_concrete",
                "d": "minecraft:block/blue_concrete",
                "o": "minecraft:block/gold_block",
                "w": "minecraft:block/white_concrete",
                "n": "minecraft:block/black_concrete",
                "l": "minecraft:block/sea_lantern",
                "g": "minecraft:block/light_blue_stained_glass",
                "particle": "minecraft:block/light_blue_concrete\"""";

    private record Box(double x0, double y0, double z0, double x1, double y1, double z1, String tex) { }

    private static Box b(double x0, double y0, double z0, double x1, double y1, double z1, String tex) {
        return new Box(x0, y0, z0, x1, y1, z1, tex);
    }

    public static void main(String[] args) throws IOException {
        Path pack = Path.of(args.length > 0 ? args[0] : "pack");
        write(pack, "pet_robot", List.of(
                b(5, 0, 7, 7, 3, 9, "o"), b(9, 0, 7, 11, 3, 9, "o"),           // jambes
                b(4, 3, 5, 12, 9, 11, "b"),                                     // corps
                b(6, 5, 4.6, 10, 8, 5, "o"),                                    // plaque dorée
                b(7.5, 6, 4.4, 8.5, 7, 4.6, "l"),                                // voyant
                b(2, 4, 7, 4, 8, 9, "d"), b(12, 4, 7, 14, 8, 9, "d"),           // bras
                b(1.5, 3, 6.5, 4.5, 4, 9.5, "o"), b(11.5, 3, 6.5, 14.5, 4, 9.5, "o"), // mains
                b(5, 9, 6, 11, 14, 10, "w"),                                    // tête
                b(6, 10.5, 5.6, 10, 12.5, 6, "n"),                              // visière
                b(6.5, 11, 5.4, 7.5, 12, 5.6, "l"), b(8.5, 11, 5.4, 9.5, 12, 5.6, "l"), // yeux
                b(7.5, 14, 7.5, 8.5, 16, 8.5, "o"), b(7, 16, 7, 9, 17.5, 9, "l")  // antenne
        ));
        List<Box> drone = new ArrayList<>(List.of(
                b(5, 6, 5, 11, 9, 11, "w"),                                     // coque
                b(6, 9, 6, 10, 10.5, 10, "g"),                                  // dôme
                b(7, 6.5, 4.6, 9, 8.5, 5, "l"),                                 // œil
                b(1, 7.5, 7.5, 15, 8.5, 8.5, "o"), b(7.5, 7.5, 1, 8.5, 8.5, 15, "o"), // bras en croix
                b(5.5, 4, 6, 10.5, 5, 7, "d"), b(5.5, 4, 9, 10.5, 5, 10, "d"),  // patins
                b(6.5, 5, 6.3, 7.5, 6, 6.7, "d"), b(8.5, 5, 9.3, 9.5, 6, 9.7, "d")));
        for (double[] c : new double[][]{{1.5, 8}, {14.5, 8}, {8, 1.5}, {8, 14.5}}) {
            drone.add(b(c[0] - 0.5, 8.5, c[1] - 0.5, c[0] + 0.5, 9.2, c[1] + 0.5, "o"));       // axe
            drone.add(b(c[0] - 3, 9.2, c[1] - 0.5, c[0] + 3, 9.6, c[1] + 0.5, "b"));           // pales
            drone.add(b(c[0] - 0.5, 9.2, c[1] - 3, c[0] + 0.5, 9.6, c[1] + 3, "b"));
        }
        write(pack, "pet_drone", drone);
        write(pack, "pet_chat", List.of(
                b(4.5, 0, 5.5, 6.5, 2, 7.5, "o"), b(9.5, 0, 5.5, 11.5, 2, 7.5, "o"),   // pattes avant
                b(4.5, 0, 10.5, 6.5, 2, 12.5, "o"), b(9.5, 0, 10.5, 11.5, 2, 12.5, "o"), // pattes arrière
                b(4, 2, 5, 12, 7, 13, "w"),                                     // combinaison
                b(4, 4, 4.9, 12, 5, 13.1, "b"),                                 // bande bleue
                b(6, 7, 8, 10, 9, 12, "b"), b(6.5, 9, 9, 9.5, 10, 11, "o"),     // réservoir d'oxygène
                b(7.5, 5, 13, 8.5, 6, 16, "o"), b(7.5, 6, 15, 8.5, 10, 16, "o"), // queue
                b(5, 6, 1.5, 11, 11.5, 6.5, "o"),                               // tête
                b(5.5, 11.5, 2.5, 7, 13.5, 4, "o"), b(9, 11.5, 2.5, 10.5, 13.5, 4, "o"), // oreilles
                b(6, 8.5, 1.3, 7.5, 10, 1.5, "l"), b(8.5, 8.5, 1.3, 10, 10, 1.5, "l"),   // yeux
                b(7.5, 7.5, 1.3, 8.5, 8.3, 1.5, "n"),                           // museau
                b(3.8, 5.3, 0.6, 12.2, 14.3, 7.4, "g")                          // casque de verre
        ));
        List<Box> meduse = new ArrayList<>(List.of(
                b(3, 8, 3, 13, 13, 13, "g"),                                    // ombrelle
                b(5, 13, 5, 11, 15, 11, "g"),
                b(6, 9, 6, 10, 12, 10, "l"),                                    // cœur lumineux
                b(2.5, 8, 2.5, 13.5, 8.8, 13.5, "o")));                         // collerette dorée
        double[][] tent = {{4, 1, 4}, {11, 2, 4}, {4, 3, 11}, {11, 0, 11}, {7.5, 0, 3.5}, {7.5, 2, 11.5}, {3.5, 1, 7.5}, {11.5, 3, 7.5}};
        for (int i = 0; i < tent.length; i++) {
            double[] t = tent[i];
            meduse.add(b(t[0], t[1], t[2], t[0] + 1, 8, t[2] + 1, i % 2 == 0 ? "b" : "o"));
        }
        write(pack, "pet_meduse", meduse);
        write(pack, "pet_satellite", List.of(
                b(5, 5, 5, 11, 11, 11, "o"),                                    // corps doré
                b(4.8, 7.5, 4.8, 11.2, 8.5, 11.2, "w"),                         // ceinture
                b(-6, 5.5, 7.6, 4, 10.5, 8.4, "d"), b(12, 5.5, 7.6, 22, 10.5, 8.4, "d"), // panneaux solaires
                b(-6, 7.9, 7.4, 4, 8.1, 8.6, "b"), b(12, 7.9, 7.4, 22, 8.1, 8.6, "b"),   // lignes des panneaux
                b(-1.1, 5.5, 7.4, -0.9, 10.5, 8.6, "b"), b(16.9, 5.5, 7.4, 17.1, 10.5, 8.6, "b"),
                b(4, 7.5, 7.5, 5, 8.5, 8.5, "w"), b(11, 7.5, 7.5, 12, 8.5, 8.5, "w"), // bras
                b(5.5, 11, 5.5, 10.5, 11.5, 10.5, "w"),                         // parabole
                b(7.5, 11.5, 7.5, 8.5, 14, 8.5, "w"), b(7, 14, 7, 9, 15, 9, "l"), // antenne
                b(7, 3.5, 7, 9, 5, 9, "b")                                      // propulseur
        ));
        System.out.println("Modèles des pets écrits dans " + pack.toAbsolutePath());
    }

    private static void write(Path pack, String name, List<Box> boxes) throws IOException {
        Path assets = pack.resolve("assets/orbitdev");
        Files.createDirectories(assets.resolve("items"));
        Files.createDirectories(assets.resolve("models/item"));
        Files.writeString(assets.resolve("items/" + name + ".json"),
                "{\n  \"model\": {\"type\": \"minecraft:model\", \"model\": \"orbitdev:item/" + name + "\"}\n}\n", StandardCharsets.UTF_8);
        StringBuilder s = new StringBuilder("{\n  \"textures\": {\n    ").append(TEXTURES).append("\n  },\n  \"elements\": [\n");
        for (int i = 0; i < boxes.size(); i++) {
            Box x = boxes.get(i);
            s.append("    {\"from\": [").append(n(x.x0)).append(", ").append(n(x.y0)).append(", ").append(n(x.z0))
                    .append("], \"to\": [").append(n(x.x1)).append(", ").append(n(x.y1)).append(", ").append(n(x.z1)).append("], ");
            if (x.tex.equals("l")) {
                s.append("\"light_emission\": 15, ");
            }
            s.append("\"faces\": {");
            String[] faces = {"north", "east", "south", "west", "up", "down"};
            for (int f = 0; f < faces.length; f++) {
                s.append('"').append(faces[f]).append("\": {\"uv\": [0, 0, 16, 16], \"texture\": \"#").append(x.tex).append("\"}").append(f < 5 ? ", " : "");
            }
            s.append("}}").append(i < boxes.size() - 1 ? ",\n" : "\n");
        }
        s.append("  ]\n}\n");
        Files.writeString(assets.resolve("models/item/" + name + ".json"), s.toString(), StandardCharsets.UTF_8);
    }

    private static String n(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v);
    }
}
