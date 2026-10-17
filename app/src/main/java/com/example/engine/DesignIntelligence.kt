package com.example.engine

import java.util.Locale

/**
 * Design Intelligence Engine for NexusAI.
 * Integrates methodologies from Impeccable (Paul Bakaus), UI UX Pro Max (NextLevelBuilder),
 * and Taste Skill (Leonxlnx).
 *
 * Provides:
 * 1. Design Direction Inference & Style Presets (Minimalist, Neo-Brutalist, Modern SaaS, etc.)
 * 2. Deterministic AI Slop Detector & UX Auditor
 * 3. System Prompt Directives for Frontend & Game Generation
 * 4. Actionable Self-Critique & Auto-Polish Prompts
 */
object DesignIntelligence {

    enum class DesignStyle(
        val displayName: String,
        val tagline: String,
        val description: String
    ) {
        MINIMALIST(
            "Minimalist Precision",
            "High negative space, typographic restraint, surgical clarity",
            "Monochrome or single muted accent, generous whitespace, strict 8pt rhythm, subtle hairline borders."
        ),
        NEO_BRUTALIST(
            "Neo-Brutalism",
            "High contrast, bold borders, hard offset drop shadows",
            "Heavy 2-3px solid black outlines, unblurred 4px offset box-shadows, punchy high-saturation accents, bold grotesque type."
        ),
        MODERN_SAAS(
            "Modern SaaS & Dashboard",
            "Refined slate surfaces, 8px-12px radiuses, semantic accents",
            "Subtle surface elevation, neutral zinc/slate backgrounds, semantic primary accent, clean data density, micro-interactions."
        ),
        EDITORIAL(
            "Editorial & Publication",
            "Serif headlines, generous line-height, asymmetric magazine layout",
            "Warm paper/ink tones, Playfair/Georgia typography paired with clean sans body, rich narrative hierarchy, multi-column flow."
        ),
        CYBERPUNK_ARCADE(
            "Cyberpunk & Retro Arcade",
            "Deep obsidian base, glowing neon luminescence, monospace telemetry",
            "Canvas-based rendering, neon cyan/magenta/lime highlights, CRT or glowing border accents, high-framerate interactive canvas."
        ),
        LUXURY_DARK(
            "Luxury Obsidian",
            "Deep charcoal canvas, muted champagne/gold accents, understated prestige",
            "Rich deep black (#0b0b0e), muted gold/champagne metallic accents, delicate typography, subtle ambient glows."
        ),
        SWISS_INTERNATIONAL(
            "Swiss International",
            "Strict mathematical grid, bold sans-serif, asymmetrical balance",
            "Grid-aligned structures, high-contrast black/white with bold International Orange or Swiss Red accents, objective hierarchy."
        )
    }

    data class ColorPalette(
        val name: String,
        val background: String,
        val surface: String,
        val primary: String,
        val secondary: String,
        val accent: String,
        val border: String,
        val textPrimary: String,
        val textMuted: String
    )

    data class TypographyPairing(
        val name: String,
        val headingFont: String,
        val bodyFont: String,
        val monospaceFont: String,
        val baseSize: String = "16px",
        val scaleRatio: Double = 1.25 // Major Third
    )

    data class DesignSystemSpec(
        val style: DesignStyle,
        val palette: ColorPalette,
        val typography: TypographyPairing,
        val spacingScale: List<Int> = listOf(4, 8, 12, 16, 24, 32, 48, 64),
        val borderRadius: String,
        val shadowStyle: String,
        val keyRules: List<String>
    )

    // Predefined Color Palettes for each Style
    val PALETTES = mapOf(
        DesignStyle.MINIMALIST to ColorPalette(
            name = "Mono Clarity",
            background = "#fcfcfc",
            surface = "#ffffff",
            primary = "#18181b",
            secondary = "#71717a",
            accent = "#2563eb",
            border = "#e4e4e7",
            textPrimary = "#09090b",
            textMuted = "#71717a"
        ),
        DesignStyle.NEO_BRUTALIST to ColorPalette(
            name = "Electric Pop Brutalism",
            background = "#fef08a", // vibrant warm yellow
            surface = "#ffffff",
            primary = "#000000",
            secondary = "#f43f5e",
            accent = "#06b6d4",
            border = "#000000",
            textPrimary = "#000000",
            textMuted = "#3f3f46"
        ),
        DesignStyle.MODERN_SAAS to ColorPalette(
            name = "Slate & Indigo SaaS",
            background = "#0f172a",
            surface = "#1e293b",
            primary = "#6366f1",
            secondary = "#38bdf8",
            accent = "#10b981",
            border = "#334155",
            textPrimary = "#f8fafc",
            textMuted = "#94a3b8"
        ),
        DesignStyle.EDITORIAL to ColorPalette(
            name = "Ink & Vellum",
            background = "#fcfbf7",
            surface = "#ffffff",
            primary = "#27272a",
            secondary = "#9a3412",
            accent = "#b45309",
            border = "#e7e5e4",
            textPrimary = "#1c1917",
            textMuted = "#78716c"
        ),
        DesignStyle.CYBERPUNK_ARCADE to ColorPalette(
            name = "Neon Grid Matrix",
            background = "#090a10",
            surface = "#121420",
            primary = "#00f0ff",
            secondary = "#ff0055",
            accent = "#39ff14",
            border = "#262940",
            textPrimary = "#f0f4fc",
            textMuted = "#7880a0"
        ),
        DesignStyle.LUXURY_DARK to ColorPalette(
            name = "Obsidian & Champagne",
            background = "#0a0a0c",
            surface = "#141418",
            primary = "#d4af37",
            secondary = "#e2d4a8",
            accent = "#f3e8c8",
            border = "#272730",
            textPrimary = "#f4f4f5",
            textMuted = "#a1a1aa"
        ),
        DesignStyle.SWISS_INTERNATIONAL to ColorPalette(
            name = "Zurich International",
            background = "#ffffff",
            surface = "#f4f4f5",
            primary = "#e11d48", // International red
            secondary = "#18181b",
            accent = "#0284c7",
            border = "#18181b",
            textPrimary = "#09090b",
            textMuted = "#52525b"
        )
    )

    val TYPOGRAPHY_PAIRINGS = mapOf(
        DesignStyle.MINIMALIST to TypographyPairing(
            "Inter Restraint",
            "-apple-system, BlinkMacSystemFont, 'Inter', sans-serif",
            "-apple-system, BlinkMacSystemFont, 'Inter', sans-serif",
            "'JetBrains Mono', monospace"
        ),
        DesignStyle.NEO_BRUTALIST to TypographyPairing(
            "Heavy Grotesque",
            "'Space Grotesk', 'Impact', sans-serif",
            "'Inter', sans-serif",
            "'Space Mono', monospace"
        ),
        DesignStyle.MODERN_SAAS to TypographyPairing(
            "Geist & Inter",
            "'Geist', 'Inter', sans-serif",
            "'Inter', system-ui, sans-serif",
            "'JetBrains Mono', 'Fira Code', monospace"
        ),
        DesignStyle.EDITORIAL to TypographyPairing(
            "Playfair & Source",
            "'Playfair Display', Georgia, serif",
            "'Source Serif 4', Georgia, serif",
            "'Courier Prime', monospace"
        ),
        DesignStyle.CYBERPUNK_ARCADE to TypographyPairing(
            "Arcade & Mono",
            "'Press Start 2P', 'Chakra Petch', monospace, sans-serif",
            "'Chakra Petch', 'Share Tech Mono', sans-serif",
            "'VT323', monospace"
        ),
        DesignStyle.LUXURY_DARK to TypographyPairing(
            "Cormorant & Montserrat",
            "'Cormorant Garamond', Georgia, serif",
            "'Montserrat', 'Inter', sans-serif",
            "'JetBrains Mono', monospace"
        ),
        DesignStyle.SWISS_INTERNATIONAL to TypographyPairing(
            "Akzidenz & Neue Haas",
            "'Helvetica Neue', 'Arial Black', sans-serif",
            "'Helvetica Neue', Arial, sans-serif",
            "'IBM Plex Mono', monospace"
        )
    )

    /**
     * Infers design direction and constructs a concrete design system based on prompt intent.
     */
    fun inferDesignDirection(prompt: String): DesignSystemSpec {
        val lower = prompt.lowercase(Locale.ROOT)
        val style = when {
            lower.contains("game") || lower.contains("arcade") || lower.contains("retro") ||
                    lower.contains("pong") || lower.contains("snake") || lower.contains("invader") ||
                    lower.contains("cyberpunk") || lower.contains("synthwave") || lower.contains("neon") ->
                DesignStyle.CYBERPUNK_ARCADE

            lower.contains("brutalis") || lower.contains("neo-brut") || lower.contains("quirky") ||
                    lower.contains("pop art") || lower.contains("bold shadow") || lower.contains("sticker") ->
                DesignStyle.NEO_BRUTALIST

            lower.contains("editorial") || lower.contains("magazine") || lower.contains("newspaper") ||
                    lower.contains("article") || lower.contains("blog") || lower.contains("journal") ||
                    lower.contains("book") || lower.contains("publishing") ->
                DesignStyle.EDITORIAL

            lower.contains("luxury") || lower.contains("exclusive") || lower.contains("jewelry") ||
                    lower.contains("hotel") || lower.contains("champagne") || lower.contains("gold") ||
                    lower.contains("prestige") ->
                DesignStyle.LUXURY_DARK

            lower.contains("swiss") || lower.contains("bauhaus") || lower.contains("poster") ||
                    lower.contains("grid system") || lower.contains("akzidenz") ->
                DesignStyle.SWISS_INTERNATIONAL

            lower.contains("minimal") || lower.contains("clean") || lower.contains("portfolio") ||
                    lower.contains("resume") || lower.contains("personal site") ->
                DesignStyle.MINIMALIST

            else ->
                DesignStyle.MODERN_SAAS
        }

        val palette = PALETTES[style] ?: PALETTES[DesignStyle.MODERN_SAAS]!!
        val typo = TYPOGRAPHY_PAIRINGS[style] ?: TYPOGRAPHY_PAIRINGS[DesignStyle.MODERN_SAAS]!!

        val radius = when (style) {
            DesignStyle.NEO_BRUTALIST -> "0px"
            DesignStyle.SWISS_INTERNATIONAL -> "0px"
            DesignStyle.MINIMALIST -> "4px"
            DesignStyle.MODERN_SAAS -> "10px"
            DesignStyle.CYBERPUNK_ARCADE -> "2px"
            DesignStyle.LUXURY_DARK -> "6px"
            DesignStyle.EDITORIAL -> "4px"
        }

        val shadow = when (style) {
            DesignStyle.NEO_BRUTALIST -> "4px 4px 0px #000000"
            DesignStyle.CYBERPUNK_ARCADE -> "0 0 15px rgba(0, 240, 255, 0.4)"
            DesignStyle.MODERN_SAAS -> "0 4px 20px -2px rgba(0, 0, 0, 0.3)"
            DesignStyle.LUXURY_DARK -> "0 8px 30px rgba(0, 0, 0, 0.6)"
            else -> "none"
        }

        val rules = listOf(
            "Embrace the ${style.displayName} aesthetic with intentional commitment.",
            "Avoid AI Slop: No unconstrained purple-on-white gradients, no cards-inside-cards without purpose.",
            "Enforce a strict 48px touch target for all buttons and interactive elements.",
            "Ensure meta viewport tag is present with width=device-width, initial-scale=1.0.",
            "Define explicit :hover, :active, and :focus-visible states for all interactive elements.",
            "For canvas/games: use requestAnimationFrame, handle window resize, high-DPI scaling, and touch listeners."
        )

        return DesignSystemSpec(
            style = style,
            palette = palette,
            typography = typo,
            borderRadius = radius,
            shadowStyle = shadow,
            keyRules = rules
        )
    }

    /**
     * Builds comprehensive prompt directives injected into the model to produce high-taste,
     * non-generic, anti-slop frontend and game artifacts.
     */
    fun buildDesignDirectives(prompt: String): String {
        val lower = prompt.lowercase(Locale.ROOT)
        val isUiOrCodePrompt = lower.contains("html") || lower.contains("website") || lower.contains("app") ||
                lower.contains("ui") || lower.contains("css") || lower.contains("javascript") ||
                lower.contains("game") || lower.contains("canvas") || lower.contains("dashboard") ||
                lower.contains("component") || lower.contains("prototype") || lower.contains("artifact") ||
                lower.contains("frontend") || lower.contains("interface")

        if (!isUiOrCodePrompt) return ""

        val spec = inferDesignDirection(prompt)

        return """
        [DESIGN INTELLIGENCE & TASTE GUIDELINES (Impeccable & Taste Framework)]
        Active Design Direction: ${spec.style.displayName} ("${spec.style.tagline}")
        - Visual Philosophy: ${spec.style.description}
        - Palette Specification:
            --bg: ${spec.palette.background}
            --surface: ${spec.palette.surface}
            --primary: ${spec.palette.primary}
            --secondary: ${spec.palette.secondary}
            --accent: ${spec.palette.accent}
            --border: ${spec.palette.border}
            --text-primary: ${spec.palette.textPrimary}
            --text-muted: ${spec.palette.textMuted}
        - Typography: Heading: ${spec.typography.headingFont} | Body: ${spec.typography.bodyFont} | Mono: ${spec.typography.monospaceFont}
        - Radiuses & Shadows: Radius: ${spec.borderRadius} | Shadow: ${spec.shadowStyle}

        MANDATORY ANTI-SLOP & TECHNICAL STANDARDS:
        1. NO AI SLOP: Strictly avoid cliché purple-on-white gradients (#6366f1 to #8b5cf6), repetitive card-inside-card syndrome, and meaningless decorative borders.
        2. VIEWPORT & RESPONSIVENESS: Every HTML file MUST contain `<meta name="viewport" content="width=device-width, initial-scale=1.0">` and adapt gracefully from mobile (390px) to desktop without horizontal scroll.
        3. TOUCH & ACCESSIBILITY: Interactive targets (buttons, inputs, links) MUST have min-height: 48px or padding ensuring >=48x48px hit area. Contrast ratio must satisfy WCAG AA (>= 4.5:1).
        4. STATES & MOTION: Every interactive element must include `:hover`, `:active`, and `:focus-visible` states with transitions <= 250ms.
        5. CANVASES & GAMES: If building a game or canvas application:
           - Use `requestAnimationFrame` for loop timing (never raw setInterval).
           - Scale canvas dynamically with `window.devicePixelRatio` for razor-sharp rendering on high-DPI displays.
           - Provide dual controls: both keyboard (WASD/Arrows/Space) AND on-screen touch/gesture controls for mobile playability.
           - Provide clear game states: Start, Playing, Paused, Game Over, and Restart.
        6. COMPLETE STANDALONE CODE: Provide the entire working HTML with embedded CSS `<style>` and JavaScript `<script>` so it can run immediately in an Android WebView without external broken CDN dependencies.
        """.trimIndent()
    }

    // -------------------------------------------------------------
    // DETERMINISTIC AI SLOP DETECTOR & UX AUDITOR
    // -------------------------------------------------------------

    enum class IssueSeverity { HIGH, MEDIUM, LOW }

    data class AuditIssue(
        val ruleId: String,
        val title: String,
        val severity: IssueSeverity,
        val description: String,
        val recommendation: String,
        val penalty: Int
    )

    data class PassedRule(
        val ruleId: String,
        val title: String,
        val detail: String
    )

    data class DesignAuditReport(
        val score: Int, // 0 - 100
        val grade: String,
        val inferredStyle: DesignStyle,
        val passedRules: List<PassedRule>,
        val issues: List<AuditIssue>,
        val hasGameLoop: Boolean,
        val isMobileOptimized: Boolean
    ) {
        fun toPolishPrompt(): String {
            if (issues.isEmpty()) {
                return "The design audit scored $score/100 (Grade: $grade). The code meets Impeccable and Taste standards."
            }
            val sb = StringBuilder()
            sb.appendLine("DESIGN AUDIT CRITIQUE (Score: $score/100 · Grade: $grade):")
            sb.appendLine("The following defects and AI-slop indicators were detected in the code:")
            issues.forEachIndexed { i, issue ->
                sb.appendLine("${i + 1}. [${issue.severity}] ${issue.title}: ${issue.description}")
                sb.appendLine("   Actionable Fix: ${issue.recommendation}")
            }
            sb.appendLine()
            sb.appendLine("Please apply Impeccable Polish to rewrite the code, resolving every issue above while adhering to the ${inferredStyle.displayName} aesthetic.")
            return sb.toString()
        }
    }

    /**
     * Executes a deterministic audit of HTML/CSS/JS code inspired by Impeccable's 59 detector rules.
     */
    fun auditCode(code: String, language: String = "html"): DesignAuditReport {
        val issues = mutableListOf<AuditIssue>()
        val passed = mutableListOf<PassedRule>()

        val lowerCode = code.lowercase(Locale.ROOT)
        val spec = inferDesignDirection(code)
        val style = spec.style
        val isHtml = language == "html" || lowerCode.contains("<!doctype html") || lowerCode.contains("<html") || lowerCode.contains("<div")

        var isMobileOptimized = false
        var hasGameLoop = false

        // Rule 1: Viewport Meta Tag (HTML)
        if (isHtml) {
            val hasViewport = lowerCode.contains("<meta") && lowerCode.contains("viewport") && lowerCode.contains("width=device-width")
            if (hasViewport) {
                passed.add(PassedRule("R1_VIEWPORT", "Responsive Viewport", "Meta viewport configured with width=device-width."))
                isMobileOptimized = true
            } else {
                issues.add(AuditIssue(
                    ruleId = "R1_VIEWPORT",
                    title = "Missing Responsive Viewport Meta",
                    severity = IssueSeverity.HIGH,
                    description = "HTML lacks `<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">`. Mobile rendering will be zoomed out or misaligned.",
                    recommendation = "Add `<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">` in the `<head>`.",
                    penalty = 15
                ))
            }
        }

        // Rule 2: Anti-Slop AI Gradient & Purple Cliché Detector
        val genericPurpleRegex = Regex("linear-gradient\\([^)]*(#6366f1|#8b5cf6|#667eea|#764ba2|indigo|purple)[^)]*\\)", RegexOption.IGNORE_CASE)
        val matchesGenericPurple = genericPurpleRegex.containsMatchIn(code)
        if (matchesGenericPurple && style != DesignStyle.CYBERPUNK_ARCADE) {
            issues.add(AuditIssue(
                ruleId = "R2_AI_GRADIENT",
                title = "Generic AI Purple Gradient Slop",
                severity = IssueSeverity.MEDIUM,
                description = "Detected generic saturated purple/indigo gradient commonly associated with generic AI templates.",
                recommendation = "Replace with intentional aesthetic background tokens or crisp solid surfaces matching ${style.displayName}.",
                penalty = 12
            ))
        } else {
            passed.add(PassedRule("R2_AI_GRADIENT", "Anti-Slop Color Check", "No generic cliché AI purple gradients detected."))
        }

        // Rule 3: Touch Target & Button Hit Area
        val hasButton = lowerCode.contains("<button") || lowerCode.contains("class=\"btn") || lowerCode.contains("role=\"button\"")
        if (hasButton) {
            val hasMinHeightOrPadding = lowerCode.contains("min-height:") || lowerCode.contains("padding:") || lowerCode.contains("height: 4") || lowerCode.contains("height: 5")
            if (hasMinHeightOrPadding) {
                passed.add(PassedRule("R3_TOUCH_TARGETS", "Accessible Touch Targets", "Buttons feature explicit padding or height."))
            } else {
                issues.add(AuditIssue(
                    ruleId = "R3_TOUCH_TARGETS",
                    title = "Sub-Standard Touch Target Size",
                    severity = IssueSeverity.HIGH,
                    description = "Buttons or clickable controls do not specify minimum touch size (48x48px or adequate padding). This causes mis-taps on mobile devices.",
                    recommendation = "Add `min-height: 48px; min-width: 48px; padding: 12px 20px;` to buttons.",
                    penalty = 14
                ))
            }
        }

        // Rule 4: Micro-Interactions & States (:hover, :focus-visible, :active)
        val hasHover = lowerCode.contains(":hover")
        val hasActive = lowerCode.contains(":active")
        val hasFocus = lowerCode.contains(":focus")
        if (hasHover || hasActive || hasFocus) {
            passed.add(PassedRule("R4_INTERACTIONS", "Interactive Micro-States", "Code provides dedicated pseudo-class states (:hover, :active, or :focus)."))
        } else if (hasButton || lowerCode.contains("<a ") || lowerCode.contains("<input")) {
            issues.add(AuditIssue(
                ruleId = "R4_INTERACTIONS",
                title = "Missing Interactive Feedback States",
                severity = IssueSeverity.MEDIUM,
                description = "Interactive elements lack `:hover`, `:active`, or `:focus-visible` states, resulting in a lifeless, static feel.",
                recommendation = "Add `:hover { transform: translateY(-2px); }` and `:active { transform: translateY(0); }` with smooth transition.",
                penalty = 10
            ))
        }

        // Rule 5: Typography Scale & Line Height
        val hasLineHeight = lowerCode.contains("line-height")
        val hasHeading = lowerCode.contains("<h1") || lowerCode.contains("<h2") || lowerCode.contains("font-weight")
        if (hasLineHeight && hasHeading) {
            passed.add(PassedRule("R5_TYPOGRAPHY", "Typographic Hierarchy", "Explicit heading hierarchy and line-height spacing configured."))
        } else if (!hasLineHeight) {
            issues.add(AuditIssue(
                ruleId = "R5_TYPOGRAPHY",
                title = "Missing Body Line-Height",
                severity = IssueSeverity.LOW,
                description = "Text lacks explicit `line-height` definition (ideal: 1.5 - 1.6 for body copy), causing dense and hard-to-read typography.",
                recommendation = "Add `line-height: 1.6;` to body text or root container.",
                penalty = 6
            ))
        }

        // Rule 6: Fluid / Responsive Layout Check
        val hasFixedHardcodedWidth = lowerCode.contains("width: 800px") || lowerCode.contains("width: 900px") || lowerCode.contains("width: 1000px") || lowerCode.contains("width: 1200px")
        val hasFlexOrGrid = lowerCode.contains("display: flex") || lowerCode.contains("display: grid") || lowerCode.contains("max-width:") || lowerCode.contains("width: 100%")
        if (hasFixedHardcodedWidth && !lowerCode.contains("max-width: 100%")) {
            issues.add(AuditIssue(
                ruleId = "R6_RESPONSIVENESS",
                title = "Fixed Hardcoded Width Overflow",
                severity = IssueSeverity.HIGH,
                description = "Fixed width (>800px) without `max-width: 100%` will cause horizontal overflow clipping on mobile screens.",
                recommendation = "Change to `width: 100%; max-width: 800px;` with `margin: 0 auto; box-sizing: border-box;`.",
                penalty = 15
            ))
        } else if (hasFlexOrGrid) {
            passed.add(PassedRule("R6_RESPONSIVENESS", "Fluid Layout Structure", "Modern fluid layout with flexbox/grid/max-width detected."))
        }

        // Rule 7: Games & Canvas Loop Optimization (if canvas or game detected)
        val isCanvasOrGame = lowerCode.contains("<canvas") || lowerCode.contains("getcontext") || lowerCode.contains("requestanimationframe")
        if (isCanvasOrGame) {
            val hasRaf = lowerCode.contains("requestanimationframe")
            val hasResize = lowerCode.contains("resize") || lowerCode.contains("devicepixelratio")
            val hasTouch = lowerCode.contains("touchstart") || lowerCode.contains("pointerdown") || lowerCode.contains("touch")

            if (hasRaf) {
                hasGameLoop = true
                passed.add(PassedRule("R7_GAME_LOOP", "Smooth Animation Loop", "Canvas utilizes requestAnimationFrame for 60FPS fluid execution."))
            } else {
                issues.add(AuditIssue(
                    ruleId = "R7_GAME_LOOP",
                    title = "Sub-Optimal Canvas Game Loop",
                    severity = IssueSeverity.HIGH,
                    description = "Canvas application does not use `requestAnimationFrame`. Using `setInterval` causes frame tearing and high battery drain.",
                    recommendation = "Use `requestAnimationFrame(gameLoop)` for optimal 60 FPS animation.",
                    penalty = 15
                ))
            }

            if (!hasTouch) {
                issues.add(AuditIssue(
                    ruleId = "R7_GAME_TOUCH",
                    title = "Missing Touch Controls in Game",
                    severity = IssueSeverity.MEDIUM,
                    description = "Game or interactive canvas only listens to mouse/keyboard without mobile touch listeners.",
                    recommendation = "Add on-screen buttons or `canvas.addEventListener('touchstart', ...)` to allow mobile playability.",
                    penalty = 12
                ))
            } else {
                passed.add(PassedRule("R7_GAME_TOUCH", "Touch Enabled", "Canvas application includes touch/pointer handlers for mobile devices."))
            }
        }

        // Rule 8: CSS Variable / Design Token Usage
        val hasCssVariables = lowerCode.contains("var(--")
        if (hasCssVariables) {
            passed.add(PassedRule("R8_DESIGN_TOKENS", "Design Token Architecture", "CSS custom properties (tokens) utilized for coherent styling."))
        }

        // Calculate score
        val totalPenalty = issues.sumOf { it.penalty }
        val score = (100 - totalPenalty).coerceIn(10, 100)

        val grade = when {
            score >= 95 -> "A+ Impeccable"
            score >= 85 -> "A High-Fidelity"
            score >= 70 -> "B+ Good"
            score >= 50 -> "C Needs Polish"
            else -> "F Slop Detected"
        }

        return DesignAuditReport(
            score = score,
            grade = grade,
            inferredStyle = style,
            passedRules = passed,
            issues = issues,
            hasGameLoop = hasGameLoop,
            isMobileOptimized = isMobileOptimized
        )
    }

    /**
     * Builds a self-critique prompt that can be used by NexusAI to evaluate and polish its own rendered work.
     */
    fun buildSelfCritiqueWorkflow(artifactTitle: String, code: String): String {
        val audit = auditCode(code)
        return """
        [NEXUS TASTE & IMPECCABLE SELF-CRITIQUE]
        Target Artifact: $artifactTitle
        Automated Audit Score: ${audit.score}/100 (${audit.grade})
        Aesthetic Direction: ${audit.inferredStyle.displayName}
        
        Passing Metrics (${audit.passedRules.size}):
        ${audit.passedRules.joinToString("\n") { "✔ ${it.title}: ${it.detail}" }}
        
        Areas for Improvement (${audit.issues.size}):
        ${audit.issues.joinToString("\n") { "✘ [${it.severity}] ${it.title}: ${it.description} -> Fix: ${it.recommendation}" }}
        
        Refinement Plan:
        1. Eliminate all detected anti-slop patterns.
        2. Strengthen visual hierarchy, whitespace breathing room, and typography.
        3. Guarantee 100% mobile touch usability and flawless responsive viewport alignment.
        """.trimIndent()
    }
}
