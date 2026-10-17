package com.example

import com.example.engine.DesignIntelligence
import org.junit.Assert.*
import org.junit.Test

class DesignIntelligenceUnitTest {

    @Test
    fun testDesignDirectionInference_GamesAndArcade() {
        val prompt = "Create a retro 2D arcade breakout game with glowing neon balls and brick canvas"
        val spec = DesignIntelligence.inferDesignDirection(prompt)

        assertEquals(DesignIntelligence.DesignStyle.CYBERPUNK_ARCADE, spec.style)
        assertEquals("#090a10", spec.palette.background)
        assertEquals("#00f0ff", spec.palette.primary)
        assertTrue(spec.typography.headingFont.contains("Press Start 2P") || spec.typography.headingFont.contains("Chakra"))
    }

    @Test
    fun testDesignDirectionInference_Brutalism() {
        val prompt = "Build a quirky neo-brutalist landing page with bold shadows and stickers"
        val spec = DesignIntelligence.inferDesignDirection(prompt)

        assertEquals(DesignIntelligence.DesignStyle.NEO_BRUTALIST, spec.style)
        assertEquals("0px", spec.borderRadius)
        assertTrue(spec.shadowStyle.contains("4px 4px 0px"))
    }

    @Test
    fun testDesignDirectionInference_Editorial() {
        val prompt = "Design an online literary magazine article and editorial journal"
        val spec = DesignIntelligence.inferDesignDirection(prompt)

        assertEquals(DesignIntelligence.DesignStyle.EDITORIAL, spec.style)
        assertTrue(spec.typography.headingFont.contains("Playfair") || spec.typography.headingFont.contains("Georgia"))
    }

    @Test
    fun testDesignDirectionInference_Luxury() {
        val prompt = "Create an exclusive luxury watch boutique landing page"
        val spec = DesignIntelligence.inferDesignDirection(prompt)

        assertEquals(DesignIntelligence.DesignStyle.LUXURY_DARK, spec.style)
        assertEquals("#d4af37", spec.palette.primary)
    }

    @Test
    fun testDesignDirectionInference_Minimalist() {
        val prompt = "Build a clean minimal portfolio and resume"
        val spec = DesignIntelligence.inferDesignDirection(prompt)

        assertEquals(DesignIntelligence.DesignStyle.MINIMALIST, spec.style)
    }

    @Test
    fun testDesignDirectionInference_DefaultModernSaas() {
        val prompt = "Build a customer feedback analytics dashboard"
        val spec = DesignIntelligence.inferDesignDirection(prompt)

        assertEquals(DesignIntelligence.DesignStyle.MODERN_SAAS, spec.style)
    }

    @Test
    fun testBuildDesignDirectives_ContainsAntiSlopGuidelinesAndViewport() {
        val prompt = "Generate a full HTML and CSS web dashboard"
        val directives = DesignIntelligence.buildDesignDirectives(prompt)

        assertTrue("Directives must not be empty for UI prompts", directives.isNotBlank())
        assertTrue("Must include anti-slop warning", directives.contains("NO AI SLOP"))
        assertTrue("Must enforce viewport meta", directives.contains("meta name=\"viewport\""))
        assertTrue("Must enforce 48px touch targets", directives.contains("48px"))
        assertTrue("Must enforce interactive states", directives.contains(":hover") && directives.contains(":active"))
    }

    @Test
    fun testAuditCode_DetectsMissingViewport() {
        val badHtml = """
            <!DOCTYPE html>
            <html>
            <head><title>Bad Page</title></head>
            <body><h1>Hello</h1></body>
            </html>
        """.trimIndent()

        val report = DesignIntelligence.auditCode(badHtml)
        assertTrue("Missing viewport must be flagged", report.issues.any { it.ruleId == "R1_VIEWPORT" })
        assertFalse(report.isMobileOptimized)
    }

    @Test
    fun testAuditCode_DetectsGenericAiPurpleGradient() {
        val slopHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body { background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%); }
                </style>
            </head>
            <body><h1>Generic Page</h1></body>
            </html>
        """.trimIndent()

        val report = DesignIntelligence.auditCode(slopHtml)
        assertTrue("AI slop purple gradient must be detected", report.issues.any { it.ruleId == "R2_AI_GRADIENT" })
    }

    @Test
    fun testAuditCode_DetectsSubstandardTouchTargets() {
        val tinyButtonHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    button { font-size: 10px; border: none; }
                </style>
            </head>
            <body>
                <button>Tiny</button>
            </body>
            </html>
        """.trimIndent()

        val report = DesignIntelligence.auditCode(tinyButtonHtml)
        assertTrue("Substandard touch targets must be flagged", report.issues.any { it.ruleId == "R3_TOUCH_TARGETS" })
    }

    @Test
    fun testAuditCode_DetectsMissingInteractions() {
        val staticHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    button { min-height: 48px; min-width: 48px; padding: 12px; }
                </style>
            </head>
            <body>
                <button>No Hover</button>
            </body>
            </html>
        """.trimIndent()

        val report = DesignIntelligence.auditCode(staticHtml)
        assertTrue("Missing hover/active states must be flagged", report.issues.any { it.ruleId == "R4_INTERACTIONS" })
    }

    @Test
    fun testAuditCode_DetectsCanvasWithoutRaf() {
        val badGameHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
            </head>
            <body>
                <canvas id="game"></canvas>
                <script>
                    var c = document.getElementById('game');
                    var ctx = c.getContext('2d');
                    setInterval(function() { ctx.fillRect(0,0,10,10); }, 16);
                </script>
            </body>
            </html>
        """.trimIndent()

        val report = DesignIntelligence.auditCode(badGameHtml)
        assertTrue("Canvas lacking requestAnimationFrame must be flagged", report.issues.any { it.ruleId == "R7_GAME_LOOP" })
        assertFalse(report.hasGameLoop)
    }

    @Test
    fun testAuditCode_DetectsCanvasMissingTouch() {
        val mouseOnlyGameHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
            </head>
            <body>
                <canvas id="game"></canvas>
                <script>
                    var c = document.getElementById('game');
                    var ctx = c.getContext('2d');
                    function loop() { requestAnimationFrame(loop); }
                    window.addEventListener('keydown', function() {});
                    loop();
                </script>
            </body>
            </html>
        """.trimIndent()

        val report = DesignIntelligence.auditCode(mouseOnlyGameHtml)
        assertTrue(report.hasGameLoop)
        assertTrue("Canvas game without touch must be flagged", report.issues.any { it.ruleId == "R7_GAME_TOUCH" })
    }

    @Test
    fun testAuditCode_FixedHardcodedWidthCausesOverflowWarning() {
        val overflowHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    .container { width: 1000px; }
                </style>
            </head>
            <body><div class="container">Wide content</div></body>
            </html>
        """.trimIndent()

        val report = DesignIntelligence.auditCode(overflowHtml)
        assertTrue("Fixed 1000px width without max-width must be flagged", report.issues.any { it.ruleId == "R6_RESPONSIVENESS" })
    }

    @Test
    fun testAuditCode_PassesHighQualityImpeccableCode() {
        val impeccableHtml = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>High Taste Interface</title>
                <style>
                    :root {
                        --bg: #0f172a;
                        --surface: #1e293b;
                        --primary: #38bdf8;
                        --text: #f8fafc;
                    }
                    body {
                        margin: 0;
                        background: var(--bg);
                        color: var(--text);
                        font-family: -apple-system, BlinkMacSystemFont, 'Inter', sans-serif;
                        line-height: 1.6;
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        min-height: 100vh;
                    }
                    .card {
                        background: var(--surface);
                        border-radius: 12px;
                        padding: 24px;
                        width: 100%;
                        max-width: 440px;
                        box-sizing: border-box;
                    }
                    h1 {
                        font-size: 24px;
                        margin-top: 0;
                        margin-bottom: 12px;
                    }
                    .action-btn {
                        display: inline-flex;
                        align-items: center;
                        justify-content: center;
                        min-height: 48px;
                        min-width: 48px;
                        padding: 12px 24px;
                        border-radius: 8px;
                        background: var(--primary);
                        color: #0f172a;
                        font-weight: 600;
                        border: none;
                        cursor: pointer;
                        transition: transform 0.2s ease, opacity 0.2s ease;
                    }
                    .action-btn:hover {
                        transform: translateY(-2px);
                        opacity: 0.95;
                    }
                    .action-btn:active {
                        transform: translateY(0);
                    }
                    .action-btn:focus-visible {
                        outline: 2px solid #ffffff;
                        outline-offset: 2px;
                    }
                </style>
            </head>
            <body>
                <div class="card">
                    <h1>Impeccable Dashboard</h1>
                    <p>Designed with surgical precision and accessible rhythm.</p>
                    <button class="action-btn">Launch Workspace</button>
                </div>
            </body>
            </html>
        """.trimIndent()

        val report = DesignIntelligence.auditCode(impeccableHtml)
        assertTrue("High quality code must score >= 85, got ${report.score}", report.score >= 85)
        assertEquals("A+ Impeccable", report.grade)
        assertTrue("Must be marked mobile optimized", report.isMobileOptimized)
        assertTrue("Issues must be empty or minimal", report.issues.isEmpty())
        assertTrue("Passed rules must be populated", report.passedRules.size >= 5)
    }

    @Test
    fun testAuditCode_ToPolishPromptGeneratesActionableInstructions() {
        val defectiveCode = """
            <!DOCTYPE html>
            <html>
            <head><title>Test</title></head>
            <body>
                <button>Click</button>
            </body>
            </html>
        """.trimIndent()

        val report = DesignIntelligence.auditCode(defectiveCode)
        val polishPrompt = report.toPolishPrompt()

        assertTrue(polishPrompt.contains("DESIGN AUDIT CRITIQUE"))
        assertTrue(polishPrompt.contains("Missing Responsive Viewport Meta"))
        assertTrue(polishPrompt.contains("Actionable Fix"))
        assertTrue(polishPrompt.contains("Impeccable Polish"))
    }
}
