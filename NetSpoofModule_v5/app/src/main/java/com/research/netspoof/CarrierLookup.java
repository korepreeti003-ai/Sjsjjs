package com.research.netspoof;

/**
 * Detects carrier from phone number prefix and returns
 * matching MCC/MNC, operator name, ICCID prefix, etc.
 * so the spoofed identity matches the number's real network.
 */
public class CarrierLookup {

    public static class CarrierInfo {
        public final String operator;    // "Airtel"
        public final String simOp;       // "Airtel"
        public final String mccMnc;      // "40410"
        public final String country;     // "in"
        public final String iccidPrefix; // "89910"

        CarrierInfo(String op, String simOp, String mccMnc, String country, String iccid) {
            this.operator    = op;
            this.simOp       = simOp;
            this.mccMnc      = mccMnc;
            this.country     = country;
            this.iccidPrefix = iccid;
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  MAIN ENTRY — detect carrier from any phone number
    // ══════════════════════════════════════════════════════════════
    public static CarrierInfo detect(String rawNumber) {
        if (rawNumber == null || rawNumber.trim().isEmpty()) return airtel();

        // Normalize: strip spaces, dashes, brackets
        String num = rawNumber.replaceAll("[\\s\\-\\(\\)\\.]", "").trim();

        // ── India ──────────────────────────────────────────────
        if (num.startsWith("+91")) return detectIndia(num.substring(3));
        if (num.startsWith("091")) return detectIndia(num.substring(3));
        if (num.startsWith("91") && num.length() == 12) return detectIndia(num.substring(2));
        // Plain 10-digit India number
        if (num.length() == 10 && "6789".indexOf(num.charAt(0)) >= 0) return detectIndia(num);

        // ── USA / Canada (+1) ──────────────────────────────────
        if (num.startsWith("+1")) return detectUSA();
        if (num.startsWith("001")) return detectUSA();

        // ── UK (+44) ───────────────────────────────────────────
        if (num.startsWith("+44")) return uk();

        // ── UAE (+971) ─────────────────────────────────────────
        if (num.startsWith("+971")) return uae();

        // ── Saudi Arabia (+966) ────────────────────────────────
        if (num.startsWith("+966")) return saudi();

        // ── Pakistan (+92) ─────────────────────────────────────
        if (num.startsWith("+92")) return pakistan();

        // ── Bangladesh (+880) ──────────────────────────────────
        if (num.startsWith("+880")) return bangladesh();

        // ── Australia (+61) ────────────────────────────────────
        if (num.startsWith("+61")) return australia();

        // Default fallback
        return airtel();
    }

    // ══════════════════════════════════════════════════════════════
    //  INDIA — detect by first 1-2 digits of 10-digit number
    // ══════════════════════════════════════════════════════════════
    private static CarrierInfo detectIndia(String local) {
        if (local == null || local.length() < 2) return airtel();

        // Remove leading zeros if any
        while (local.startsWith("0") && local.length() > 1) local = local.substring(1);

        char d1 = local.charAt(0);
        char d2 = local.length() > 1 ? local.charAt(1) : '0';

        switch (d1) {
            // ── Jio: ALL 60xx–69xx ──────────────────────────
            case '6':
                return jio();

            // ── 70xx: Jio, 71–79: Airtel ───────────────────
            case '7':
                if (d2 == '0') return jio();       // 70xx = Jio in most circles
                if (d2 == '2') return airtel();    // 72xx
                if (d2 == '3') return airtel();    // 73xx
                if (d2 == '4') return airtel();    // 74xx
                if (d2 == '5') return jio();       // 75xx = Jio
                if (d2 == '6') return jio();       // 76xx = Jio
                if (d2 == '7') return airtel();    // 77xx = Airtel
                if (d2 == '8') return airtel();    // 78xx = Airtel
                if (d2 == '9') return airtel();    // 79xx = Airtel
                return airtel();

            // ── 80–89: Airtel mostly ────────────────────────
            case '8':
                if (d2 == '0') return airtel();
                if (d2 == '1') return airtel();
                if (d2 == '2') return airtel();
                if (d2 == '3') return airtel();
                if (d2 == '4') return airtel();
                if (d2 == '5') return airtel();
                if (d2 == '6') return airtel();
                if (d2 == '7') return jio();       // 87xx = Jio
                if (d2 == '8') return airtel();
                if (d2 == '9') return jio();       // 89xx = Jio
                return airtel();

            // ── 90–94: Vi, 95–99: Airtel ────────────────────
            case '9':
                if (d2 == '0') return vi();        // 90xx = Vi
                if (d2 == '1') return vi();        // 91xx = Vi
                if (d2 == '2') return vi();        // 92xx = Vi
                if (d2 == '3') return vi();        // 93xx = Vi
                if (d2 == '4') return bsnl();      // 94xx = BSNL
                if (d2 == '5') return airtel();    // 95xx = Airtel
                if (d2 == '6') return airtel();    // 96xx = Airtel
                if (d2 == '7') return airtel();    // 97xx = Airtel
                if (d2 == '8') return airtel();    // 98xx = Airtel
                if (d2 == '9') return airtel();    // 99xx = Airtel
                return airtel();

            default:
                return airtel();
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  INDIA CARRIERS
    // ══════════════════════════════════════════════════════════════

    /** Airtel India — MCC=404, MNC=10 */
    public static CarrierInfo airtel() {
        return new CarrierInfo("Airtel", "Airtel", "40410", "in", "89910");
    }

    /** Reliance Jio — MCC=404, MNC=77 */
    public static CarrierInfo jio() {
        return new CarrierInfo("Jio 4G", "Jio", "40477", "in", "89914");
    }

    /** Vi (Vodafone-Idea) — MCC=404, MNC=20 */
    public static CarrierInfo vi() {
        return new CarrierInfo("Vi", "Vi", "40420", "in", "89912");
    }

    /** BSNL — MCC=404, MNC=27 */
    public static CarrierInfo bsnl() {
        return new CarrierInfo("BSNL Mobile", "BSNL", "40427", "in", "89913");
    }

    // ══════════════════════════════════════════════════════════════
    //  INTERNATIONAL
    // ══════════════════════════════════════════════════════════════

    /** USA — T-Mobile MCC=310 MNC=260 */
    private static CarrierInfo detectUSA() {
        return new CarrierInfo("T-Mobile", "T-Mobile", "310260", "us", "89011");
    }

    /** UK — EE MCC=234 MNC=30 */
    private static CarrierInfo uk() {
        return new CarrierInfo("EE", "EE", "23430", "gb", "89440");
    }

    /** UAE — Etisalat MCC=424 MNC=02 */
    private static CarrierInfo uae() {
        return new CarrierInfo("Etisalat", "Etisalat", "42402", "ae", "89971");
    }

    /** Saudi Arabia — STC MCC=420 MNC=01 */
    private static CarrierInfo saudi() {
        return new CarrierInfo("STC", "STC", "42001", "sa", "89966");
    }

    /** Pakistan — Jazz MCC=410 MNC=01 */
    private static CarrierInfo pakistan() {
        return new CarrierInfo("Jazz", "Jazz", "41001", "pk", "89920");
    }

    /** Bangladesh — Grameenphone MCC=470 MNC=01 */
    private static CarrierInfo bangladesh() {
        return new CarrierInfo("Grameenphone", "GP", "47001", "bd", "89880");
    }

    /** Australia — Telstra MCC=505 MNC=01 */
    private static CarrierInfo australia() {
        return new CarrierInfo("Telstra", "Telstra", "50501", "au", "89610");
    }
}
