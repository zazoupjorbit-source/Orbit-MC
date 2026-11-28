package fr.orbit.extractionmod.gun;

/** Calibres. L'objet correspondant est enregistré dans ModItems (identifiant {@link #item}). */
public enum Ammo {
	P9("munitions_9mm", "9 mm", "ammo_9mm"),
	R556("munitions_556", "5,56 mm", "ammo_5-56"),
	R762("munitions_762", "7,62 mm", "ammo_7-62"),
	G12("cartouches_12", "calibre 12", "ammo_12g");

	public final String item;
	public final String nom;
	/** Modèle du pack d'OrbitExtraction. */
	public final String modele;

	Ammo(String item, String nom, String modele) {
		this.item = item;
		this.nom = nom;
		this.modele = modele;
	}
}
