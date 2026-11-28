package fr.orbit.trieur.tri;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Filtre d'un inventaire, écrit dans le panel. Termes séparés par des virgules ; un objet passe
 * s'il correspond à au moins un terme et à aucune exclusion.
 * <ul>
 *   <li>{@code minecraft:diamond} : cet objet précis ; {@code diamond} : cet objet, quel que soit le mod</li>
 *   <li>{@code #c:ingots} : un tag d'objets (tags communs {@code c:} partagés par tous les mods)</li>
 *   <li>{@code @create} : tous les objets d'un mod</li>
 *   <li>{@code *_ore} ou {@code minecraft:*_log} : joker sur l'identifiant</li>
 *   <li>{@code ~épée} : le nom affiché contient le texte (sans tenir compte des accents)</li>
 *   <li>{@code !terme} : exclusion ; {@code *} : tout</li>
 * </ul>
 * Fonctionne avec les objets de n'importe quel mod : tout passe par les identifiants et les tags du registre.
 */
public final class Filtre {
	private final List<Predicate<ItemStack>> inclus = new ArrayList<>();
	private final List<Predicate<ItemStack>> exclus = new ArrayList<>();
	private final boolean vide;

	private Filtre(String texte) {
		for (String brut : texte.split(",")) {
			String t = brut.trim();
			if (t.isEmpty()) {
				continue;
			}
			boolean non = t.startsWith("!");
			if (non) {
				t = t.substring(1).trim();
			}
			Predicate<ItemStack> p = terme(t);
			if (p != null) {
				(non ? exclus : inclus).add(p);
			}
		}
		vide = inclus.isEmpty() && exclus.isEmpty();
	}

	public static Filtre of(String texte) {
		return new Filtre(texte == null ? "" : texte);
	}

	/** Filtre sans aucun terme : l'inventaire n'a pas de règle. */
	public boolean estVide() {
		return vide;
	}

	public boolean accepte(ItemStack stack) {
		if (vide || stack.isEmpty()) {
			return false;
		}
		for (Predicate<ItemStack> p : exclus) {
			if (p.test(stack)) {
				return false;
			}
		}
		if (inclus.isEmpty()) {
			return true; // que des exclusions : « tout sauf… »
		}
		for (Predicate<ItemStack> p : inclus) {
			if (p.test(stack)) {
				return true;
			}
		}
		return false;
	}

	private static Predicate<ItemStack> terme(String t) {
		if (t.isEmpty()) {
			return null;
		}
		if (t.equals("*")) {
			return s -> true;
		}
		if (t.startsWith("#")) {
			String nom = t.substring(1).toLowerCase(Locale.ROOT);
			Identifier id = Identifier.tryParse(nom.contains(":") ? nom : "c:" + nom);
			Identifier idVanilla = nom.contains(":") ? null : Identifier.tryParse("minecraft:" + nom);
			if (id == null) {
				return null;
			}
			TagKey<Item> tag = TagKey.create(Registries.ITEM, id);
			TagKey<Item> tag2 = idVanilla == null ? null : TagKey.create(Registries.ITEM, idVanilla);
			return s -> {
				Holder<Item> h = s.typeHolder();
				return h.is(tag) || (tag2 != null && h.is(tag2));
			};
		}
		if (t.startsWith("@")) {
			String mod = t.substring(1).trim().toLowerCase(Locale.ROOT);
			return s -> idDe(s).getNamespace().equals(mod);
		}
		if (t.startsWith("~")) {
			String morceau = simplifier(t.substring(1).trim());
			if (morceau.isEmpty()) {
				return null;
			}
			return s -> simplifier(s.getHoverName().getString()).contains(morceau);
		}
		String bas = t.toLowerCase(Locale.ROOT);
		if (bas.contains("*")) {
			StringBuilder rx = new StringBuilder();
			for (String part : bas.split("\\*", -1)) {
				if (!rx.isEmpty()) {
					rx.append(".*");
				}
				rx.append(Pattern.quote(part));
			}
			Pattern motif = Pattern.compile(rx.toString());
			boolean complet = bas.contains(":");
			return s -> {
				Identifier id = idDe(s);
				return motif.matcher(complet ? id.toString() : id.getPath()).matches();
			};
		}
		if (bas.contains(":")) {
			Identifier id = Identifier.tryParse(bas);
			return id == null ? null : s -> idDe(s).equals(id);
		}
		return s -> idDe(s).getPath().equals(bas);
	}

	public static Identifier idDe(ItemStack s) {
		return BuiltInRegistries.ITEM.getKey(s.getItem());
	}

	/** Minuscules sans accents, pour comparer les noms affichés. */
	static String simplifier(String s) {
		return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
	}
}
