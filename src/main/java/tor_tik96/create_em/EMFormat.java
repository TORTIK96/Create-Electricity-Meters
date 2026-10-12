package tor_tik96.create_em;

/** Форматирование и разбор чисел для интерфейса (общий код — нужен и серверу для сообщений). */
public final class EMFormat {
    /** 950 FE, 12 345 FE, 1.25 MFE, 3.4 GFE. */
    public static String fe(long fe) {
        long a = Math.abs(fe);
        if (a < 100_000) return String.format("%,d FE", fe);
        if (a < 100_000_000L) return trim(fe / 1_000.0) + " kFE";
        if (a < 100_000_000_000L) return trim(fe / 1_000_000.0) + " MFE";
        return trim(fe / 1_000_000_000.0) + " GFE";
    }

    /** Короткая запись числа для поля ввода: 1500 → 1500, 2000000 → 2M. */
    public static String plain(long v) {
        if (v != 0 && v % 1_000_000_000L == 0) return (v / 1_000_000_000L) + "G";
        if (v != 0 && v % 1_000_000L == 0) return (v / 1_000_000L) + "M";
        if (v != 0 && v % 1_000L == 0 && Math.abs(v) >= 10_000) return (v / 1_000L) + "k";
        return String.valueOf(v);
    }

    /** Разбор ввода: «1500», «-20», «2.5k», «3M», «1 000 000». null — ввод не число. */
    public static Long parse(String s) {
        if (s == null) return null;
        String t = s.trim().replace(" ", "").replace("_", "").replace(",", ".").toLowerCase();
        if (t.isEmpty() || t.equals("-")) return null;
        double mul = 1;
        char last = t.charAt(t.length() - 1);
        if (last == 'k' || last == 'к') mul = 1e3;
        else if (last == 'm' || last == 'м') mul = 1e6;
        else if (last == 'g' || last == 'г') mul = 1e9;
        if (mul != 1) t = t.substring(0, t.length() - 1);
        try {
            double v = Double.parseDouble(t) * mul;
            if (Double.isNaN(v) || Math.abs(v) > 9e18) return null;
            return Math.round(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Символы, которые можно вводить в числовое поле. */
    public static boolean allowed(String s) { return s.matches("[-0-9.,kKmMgGкКмМгГ _]*"); }

    private static String trim(double v) {
        String s = String.format("%.2f", v);
        return s.contains(".") || s.contains(",") ? s.replaceAll("[.,]?0+$", "") : s;
    }

    private EMFormat() {}
}
