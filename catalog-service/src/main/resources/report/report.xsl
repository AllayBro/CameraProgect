<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:output method="html" encoding="UTF-8" indent="yes"/>

    <xsl:template match="/case">
        <html>
            <head>
                <meta charset="UTF-8"/>
                <title>Penalty Report</title>
                <style>
                    body { font-family: Segoe UI, Arial, sans-serif; margin: 24px; max-width: 1100px; }
                    h2 { margin: 0 0 14px 0; }
                    h3 { margin: 18px 0 8px 0; }
                    table { border-collapse: collapse; width: 100%; margin: 8px 0 18px 0; }
                    td, th { border: 1px solid #9a9a9a; padding: 8px 10px; vertical-align: top; }
                    td:first-child { width: 260px; background: #f4f4f4; font-weight: 600; }
                    .badge { display: inline-block; padding: 2px 8px; border-radius: 999px; font-size: 12px; border: 1px solid #999; }
                    .ok { background: #eaf7ea; }
                    .warn { background: #fff6e5; }
                    .bad { background: #fdeaea; }
                    .muted { color: #666; }
                    pre { white-space: pre-wrap; word-break: break-word; background: #111; color: #eee; padding: 12px; border-radius: 10px; }
                    details > summary { cursor: pointer; margin: 6px 0; }
                </style>
            </head>

            <body>
                <h2>Case report</h2>

                <!-- безопасные числа (если вдруг прилетит "null" или "55,5") -->
                <xsl:variable name="speed" select="number(translate(record/speedKmh, ',', '.'))"/>
                <xsl:variable name="limit" select="number(translate(rules/speedLimitKmh, ',', '.'))"/>

                <h3>Record</h3>
                <table>
                    <tr><td>recordId</td><td><xsl:value-of select="record/recordId"/></td></tr>
                    <tr><td>sessionId</td><td><xsl:value-of select="record/sessionId"/></td></tr>
                    <tr><td>fileKey</td><td><xsl:value-of select="record/fileKey"/></td></tr>
                    <tr><td>droneId</td><td><xsl:value-of select="record/droneId"/></td></tr>
                    <tr><td>operatorId</td><td><xsl:value-of select="record/operatorId"/></td></tr>
                    <tr><td>takenAt</td><td><xsl:value-of select="record/takenAt"/></td></tr>
                    <tr><td>location</td><td><xsl:value-of select="concat(record/latitude, ',', record/longitude)"/></td></tr>
                    <tr><td>speedKmh</td><td><xsl:value-of select="record/speedKmh"/></td></tr>
                    <tr><td>confidence</td><td><xsl:value-of select="record/confidence"/></td></tr>
                </table>

                <h3>Rules</h3>
                <table>
                    <tr><td>speedLimitKmh</td><td><xsl:value-of select="rules/speedLimitKmh"/></td></tr>
                    <tr><td>reviewConfidenceThreshold</td><td><xsl:value-of select="rules/reviewConfidenceThreshold"/></td></tr>
                </table>

                <h3>Decision</h3>
                <table>
                    <tr>
                        <td>decisionStatus</td>
                        <td><xsl:call-template name="badge"><xsl:with-param name="value" select="record/decisionStatus"/></xsl:call-template></td>
                    </tr>
                    <tr><td>ruleCode</td><td><xsl:value-of select="record/ruleCode"/></td></tr>
                    <tr>
                        <td>requiresReview</td>
                        <td><xsl:call-template name="badge"><xsl:with-param name="value" select="record/requiresReview"/></xsl:call-template></td>
                    </tr>
                    <tr><td>amount</td><td><xsl:value-of select="record/amount"/></td></tr>

                    <tr>
                        <td>overKmh</td>
                        <td>
                            <xsl:choose>
                                <xsl:when test="string($speed)!='NaN' and string($limit)!='NaN'">
                                    <xsl:value-of select="$speed - $limit"/>
                                </xsl:when>
                                <xsl:otherwise><span class="muted">—</span></xsl:otherwise>
                            </xsl:choose>
                        </td>
                    </tr>
                </table>

                <h3>Evidence</h3>
                <details open="open">
                    <summary>raw evidenceXml</summary>
                    <pre><xsl:value-of select="record/evidenceXml"/></pre>
                </details>

            </body>
        </html>
    </xsl:template>

    <xsl:template name="badge">
        <xsl:param name="value"/>
        <xsl:variable name="v" select="normalize-space($value)"/>
        <xsl:choose>
            <xsl:when test="$v=''">
                <span class="badge"><span class="muted">—</span></span>
            </xsl:when>
            <xsl:when test="$v='APPROVED' or $v='NO_VIOLATION' or $v='false'">
                <span class="badge ok"><xsl:value-of select="$v"/></span>
            </xsl:when>
            <xsl:when test="$v='REQUIRES_REVIEW' or $v='true'">
                <span class="badge warn"><xsl:value-of select="$v"/></span>
            </xsl:when>
            <xsl:otherwise>
                <span class="badge bad"><xsl:value-of select="$v"/></span>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

</xsl:stylesheet>
