package com.jose.pokecraft.pokedex;

import java.util.Arrays;

/**
 * Cálculo de fortaleza y clasificador automático en 5 categorías. Lógica pura (sin Minecraft): recibe estadísticas
 * base y devuelve puntajes y umbrales.
 *
 * <h3>Puntaje</h3>
 * {@code Fortaleza = Vida base * (1 + Armadura base) * Ataque base}, siempre con los valores BASE del tipo
 * (AttributeSupplier por defecto: sin dificultad, efectos ni equipamiento).
 *
 * <h3>Ataque base igual a 0</h3>
 * <ul>
 *   <li>PASIVO con ataque 0: no tiene fortaleza medible. Se fuerza a la categoría más baja y su puntaje es 0.</li>
 *   <li>NEUTRAL / HOSTIL con ataque 0 (Wither, Ender Dragon... que atacan con proyectiles o mecánicas propias):
 *       para el cálculo, y SOLO para él, su ataque se toma como 1 (si no, su fortaleza sería 0).</li>
 *   <li>PASIVO con ataque mayor que 0 (conejo, cabra...): fórmula normal.</li>
 * </ul>
 *
 * <h3>Clasificación (sin umbrales fijos)</h3>
 * Se ordenan los puntajes actuales del catálogo y se reparte la lista en 5 tramos con tamaños piramidales: la
 * fracción acumulada que queda hasta el tier k es {@code F(k/5) = log1p(S*k/5) / log1p(S)}. Como log1p es cóncava,
 * cada tier es estrictamente más pequeño que el anterior (muchos Muy Débiles, pocos Muy Fuertes). Los cortes se
 * guardan como VALORES de puntaje (umbrales), no como posiciones, por lo que los empates nunca se parten.
 */
public final class StrengthClassifier {
    /**
     * Pendiente de la pirámide: a mayor valor, más mobs en los tiers bajos y menos en los altos. Es un parámetro de
     * FORMA de la distribución, no un umbral de fortaleza. Con 100 mobs reparte aprox. 48/20/14/10/8 (antes de empates).
     */
    public static final double PYRAMID_STEEPNESS = 20.0D;

    private static final int TIERS = MobStrength.values().length;
    private static final int THRESHOLD_COUNT = TIERS - 1;

    private StrengthClassifier() {}

    // ---------------------------------------------------------------------------------------------
    // Estadísticas base y puntaje
    // ---------------------------------------------------------------------------------------------

    /** Estadísticas base puras de un tipo de entidad (las mismas que muestra la PokéDex). */
    public record BaseStats(MobKind kind, float health, float armor, float damage) {
        public boolean forcedLowest() {
            return StrengthClassifier.forcedLowest(kind, damage);
        }

        public double score() {
            return StrengthClassifier.score(kind, health, armor, damage);
        }
    }

    /** Pasivo sin ataque: va siempre a la categoría más baja. */
    public static boolean forcedLowest(MobKind kind, float damage) {
        return kind == MobKind.PASSIVE && !(damage > 0.0F);
    }

    public static double score(MobKind kind, float health, float armor, float damage) {
        if (forcedLowest(kind, damage)) return 0.0D;
        // Neutral/hostil con ataque 0 -> 1 (solo para este cálculo). Pasivo con ataque > 0 usa su ataque real.
        double attack = damage > 0.0F ? damage : 1.0D;
        return Math.max(0.0D, health) * (1.0D + Math.max(0.0D, armor)) * attack;
    }

    // ---------------------------------------------------------------------------------------------
    // Clasificación
    // ---------------------------------------------------------------------------------------------

    /** Umbrales de una clasificación. Inmutable: se puede compartir entre hilos. */
    public static final class Snapshot {
        /** Sin población: todo cae en la categoría más baja. */
        public static final Snapshot EMPTY;

        static {
            double[] none = new double[THRESHOLD_COUNT];
            Arrays.fill(none, Double.POSITIVE_INFINITY);
            EMPTY = new Snapshot(none, 0);
        }

        private final double[] thresholds;
        private final int rankedCount;

        private Snapshot(double[] thresholds, int rankedCount) {
            this.thresholds = thresholds;
            this.rankedCount = rankedCount;
        }

        /** Tamaño de la población con la que se calcularon los umbrales. */
        public int rankedCount() {
            return rankedCount;
        }

        /** Copia de los umbrales: el tier k+1 empieza en {@code thresholds[k]} (inclusive). */
        public double[] thresholds() {
            return thresholds.clone();
        }

        public MobStrength tierOf(double score, boolean forcedLowest) {
            if (forcedLowest) return MobStrength.VERY_WEAK;
            if (!Double.isFinite(score)) score = 0.0D;
            int index = 0;
            for (double threshold : thresholds) {
                if (threshold <= score) index++;
                else break; // los umbrales no decrecen
            }
            return MobStrength.byIndex(index);
        }
    }

    /** Fracción acumulada de mobs (de menor a mayor fortaleza) que entra hasta la fracción x de las categorías. */
    private static double cumulativeShare(double x) {
        return Math.log1p(PYRAMID_STEEPNESS * x) / Math.log1p(PYRAMID_STEEPNESS);
    }

    /**
     * Calcula los umbrales a partir de TODOS los puntajes de la población actual (en cualquier orden).
     * Lista vacía -> {@link Snapshot#EMPTY}. Puntajes no finitos o negativos cuentan como 0.
     */
    public static Snapshot buildSnapshot(double[] rawScores) {
        int n = rawScores.length;
        if (n == 0) return Snapshot.EMPTY;

        double[] s = new double[n];
        for (int i = 0; i < n; i++) {
            double v = rawScores[i];
            s[i] = Double.isFinite(v) ? Math.max(0.0D, v) : 0.0D;
        }
        Arrays.sort(s);

        // Sin información para distinguir (un solo mob, o todos empatados): lo menor que ese valor es Muy Débil,
        // el propio valor es Normal y lo mayor, Muy Fuerte.
        if (s[0] == s[n - 1]) {
            double v = s[0];
            double up = Math.nextUp(v);
            return new Snapshot(new double[]{v, v, up, up}, n);
        }

        double[] thresholds = new double[THRESHOLD_COUNT];
        int previous = 0;
        for (int k = 1; k <= THRESHOLD_COUNT; k++) {
            // Mobs que quedan POR ENCIMA del tier k, según la pirámide.
            int above = (int) Math.round(n * (1.0D - cumulativeShare(k / (double) TIERS)));
            // Al menos 1, 2, 3, 4 mobs por encima de los tiers 1..4: ninguna categoría alta queda vacía si hay datos.
            above = Math.max(above, TIERS - k);
            // El tier más bajo conserva siempre al menos un mob.
            above = Math.min(above, n - 1);

            int boundary = Math.max(previous, resolveTies(s, n - above));
            previous = boundary;
            thresholds[k - 1] = boundary < n ? s[boundary] : Double.POSITIVE_INFINITY; // infinito = tier vacío
        }
        return new Snapshot(thresholds, n);
    }

    /**
     * Mueve un corte (índice del primer mob del tier superior) al borde de grupo empatado más cercano, para que
     * valores iguales nunca queden en tiers distintos. A igual distancia gana subir el corte (el grupo se queda en
     * el tier inferior, que es el más numeroso). Un grupo que empieza en el índice 0 siempre sube para no vaciar
     * el tier más bajo.
     */
    private static int resolveTies(double[] sorted, int boundary) {
        int n = sorted.length;
        if (boundary <= 0) return 0;
        if (boundary >= n) return n;
        if (sorted[boundary - 1] < sorted[boundary]) return boundary; // ya es borde de grupo

        double value = sorted[boundary];
        int start = boundary;
        while (start > 0 && sorted[start - 1] == value) start--;
        int end = boundary;
        while (end < n && sorted[end] == value) end++;

        if (start == 0) return end;
        return (boundary - start) < (end - boundary) ? start : end;
    }
}