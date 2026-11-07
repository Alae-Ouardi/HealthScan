package com.healthscan.app;

import static com.healthscan.app.Product.Ingredient.HIGH;
import static com.healthscan.app.Product.Ingredient.LOW;
import static com.healthscan.app.Product.Ingredient.MODERATE;
import static com.healthscan.app.Product.Ingredient.UNRATED;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HealthScan's own hazard grades for common cosmetic ingredients (estimated; see
 * MECHANICS.md). The open databases list ingredients without any risk grade, so names are
 * matched against this list; anything not on it is {@link Product.Ingredient#UNRATED}.
 * Pure Java, no Android code.
 */
final class IngredientGrades {
    private IngredientGrades() {}

    private static final Map<String, Integer> GRADES = new HashMap<>();

    static {
        grade(LOW,
                // water and simple humectants / pH adjusters
                "water", "aqua", "eau", "glycerin", "glycerine", "glycerol", "sorbitol",
                "sodium chloride", "citric acid", "lactic acid", "sodium citrate", "sodium hydroxide",
                "sodium lactate", "urea", "xanthan gum", "guar gum", "carbomer",
                // gentle cleansers and conditioners
                "cocamidopropyl betaine", "coco betaine", "decyl glucoside", "coco glucoside",
                "lauryl glucoside", "sodium cocoyl isethionate", "guar hydroxypropyltrimonium chloride",
                "cetearyl alcohol", "cetyl alcohol", "stearyl alcohol", "glyceryl stearate",
                "dimethicone", "dimethiconol", "behentrimonium chloride", "glycol distearate",
                "sodium xylenesulfonate", "sodium methyl cocoyl taurate", "coconut acid",
                "acrylates copolymer",
                // soothing and caring actives
                "panthenol", "allantoin", "niacinamide", "tocopherol", "tocopheryl acetate",
                "sodium hyaluronate", "hyaluronic acid", "aloe barbadensis leaf juice", "aloe vera",
                "bisabolol", "squalane", "caprylic capric triglyceride",
                // plant butters and oils
                "butyrospermum parkii butter", "butyrospermum parkii shea butter", "shea butter",
                "beurre de karite", "manteca de karite", "manteiga de karite",
                "cocos nucifera oil", "coconut oil", "simmondsia chinensis seed oil", "jojoba oil",
                "argania spinosa kernel oil", "argan oil", "helianthus annuus seed oil",
                "prunus amygdalus dulcis oil",
                // gentle preservatives and minerals
                "potassium sorbate", "sodium benzoate", "zinc oxide", "mica", "kaolin");

        grade(MODERATE,
                // fragrance and the fragrance allergens that must be declared in the EU
                "parfum", "fragrance", "aroma", "limonene", "linalool", "citronellol", "geraniol",
                "citral", "eugenol", "coumarin", "hexyl cinnamal", "benzyl salicylate",
                "benzyl benzoate", "benzyl alcohol", "alpha isomethyl ionone", "hydroxycitronellal",
                "amyl cinnamal", "cinnamal", "farnesol", "isoeugenol",
                // harsher surfactants and their helpers
                "sodium lauryl sulfate", "sodium laureth sulfate", "ammonium lauryl sulfate",
                "ammonium laureth sulfate", "sodium coco sulfate", "cocamide dea", "cocamide mea",
                "triethanolamine", "sodium salicylate",
                "propylene glycol",
                // preservatives and stabilisers worth limiting
                "phenoxyethanol", "chlorphenesin", "disodium edta", "tetrasodium edta", "bht",
                "salicylic acid", "retinyl palmitate",
                // UV filters, silicones and others under review
                "octocrylene", "homosalate", "butyl methoxydibenzoylmethane", "ethylhexyl methoxycinnamate",
                "cyclopentasiloxane", "polyethylene", "talc", "aluminum chlorohydrate");

        grade(HIGH,
                // formaldehyde and formaldehyde releasers
                "formaldehyde", "dmdm hydantoin", "quaternium 15", "imidazolidinyl urea",
                "diazolidinyl urea", "2 bromo 2 nitropropane 1 3 diol", "bronopol",
                // strong contact allergens
                "methylisothiazolinone", "methylchloroisothiazolinone",
                // restricted or banned in the EU, or suspected endocrine disruptors
                "triclosan", "triclocarban", "benzophenone 3", "oxybenzone", "bha",
                "butylated hydroxyanisole", "cyclotetrasiloxane", "butylphenyl methylpropional",
                "hydroquinone", "p phenylenediamine", "coal tar");
    }

    private static final Set<String> HIGH_PARABENS = new HashSet<>(Arrays.asList(
            "propyl", "butyl", "isopropyl", "isobutyl", "benzyl", "pentyl", "phenyl"));
    private static final Pattern PARENTHESES = Pattern.compile("\\(([^)]*)\\)");
    private static final Pattern PEG = Pattern.compile("^peg( \\d+.*)?$");
    private static final Pattern ETHOXYLATED = Pattern.compile(".*\\b[a-z]+eth \\d+\\b.*"); // e.g. ceteareth-20

    private static void grade(int level, String... names) {
        for (String n : names) GRADES.put(n, level);
    }

    /** Hazard grade for an ingredient name as printed on a label; the worst known name wins. */
    static int grade(String label) {
        if (label == null) return UNRATED;
        int worst = UNRATED;
        for (String name : candidates(label)) {
            Integer known = GRADES.get(name);
            worst = Math.max(worst, known != null ? known : family(name));
        }
        return worst;
    }

    /** Grades for whole ingredient families that share a name pattern. */
    private static int family(String name) {
        if (name.endsWith("paraben")) {
            String prefix = name.substring(0, name.length() - "paraben".length()).trim();
            return HIGH_PARABENS.contains(prefix) ? HIGH : MODERATE;
        }
        if (PEG.matcher(name).matches() || ETHOXYLATED.matcher(name).matches()) return MODERATE;
        if (name.startsWith("polyquaternium")) return LOW;
        return UNRATED;
    }

    /**
     * Normalised names to look up: each part of "A / B", each with and without its
     * parenthesised alias, and the alias alone ("Aqua (Water)" gives "aqua water", "aqua", "water").
     */
    private static List<String> candidates(String label) {
        List<String> out = new ArrayList<>();
        for (String part : label.split("[/,;]")) {
            add(out, part);
            add(out, PARENTHESES.matcher(part).replaceAll(" "));
            Matcher alias = PARENTHESES.matcher(part);
            while (alias.find()) add(out, alias.group(1));
        }
        return out;
    }

    private static void add(List<String> out, String raw) {
        String n = normalise(raw);
        if (!n.isEmpty()) out.add(n);
    }

    /** Lowercase, no accents, words separated by single spaces ("PEG-40" becomes "peg 40"). */
    static String normalise(String s) {
        String noAccents = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return noAccents.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }
}
