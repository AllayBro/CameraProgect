<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:output method="html" encoding="UTF-8" indent="yes"/>

    <xsl:template match="/case">
        <html>
            <head>
                <title>Penalty Report</title>
                <meta charset="UTF-8"/>
            </head>
            <body>
                <h2>Case report</h2>

                <h3>Record</h3>
                <table border="1" cellpadding="6">
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
                <table border="1" cellpadding="6">
                    <tr><td>speedLimitKmh</td><td><xsl:value-of select="rules/speedLimitKmh"/></td></tr>
                    <tr><td>reviewConfidenceThreshold</td><td><xsl:value-of select="rules/reviewConfidenceThreshold"/></td></tr>
                </table>

                <h3>Decision</h3>
                <table border="1" cellpadding="6">
                    <tr><td>decisionStatus</td><td><xsl:value-of select="record/decisionStatus"/></td></tr>
                    <tr><td>ruleCode</td><td><xsl:value-of select="record/ruleCode"/></td></tr>
                    <tr><td>amount</td><td><xsl:value-of select="record/amount"/></td></tr>
                    <tr><td>requiresReview</td><td><xsl:value-of select="record/requiresReview"/></td></tr>
                    <tr><td>overKmh</td>
                        <td>
                            <xsl:value-of select="number(record/speedKmh) - number(rules/speedLimitKmh)"/>
                        </td>
                    </tr>
                </table>

                <h3>Penalty brackets</h3>
                <table border="1" cellpadding="6">
                    <tr><th>fromOverKmh</th><th>toOverKmh</th><th>amount</th></tr>
                    <xsl:for-each select="rules/amounts/bracket">
                        <tr>
                            <td><xsl:value-of select="fromOverKmh"/></td>
                            <td><xsl:value-of select="toOverKmh"/></td>
                            <td><xsl:value-of select="amount"/></td>
                        </tr>
                    </xsl:for-each>
                </table>

                <h3>Evidence (raw)</h3>
                <pre><xsl:value-of select="record/evidenceXml"/></pre>

            </body>
        </html>
    </xsl:template>

</xsl:stylesheet>
