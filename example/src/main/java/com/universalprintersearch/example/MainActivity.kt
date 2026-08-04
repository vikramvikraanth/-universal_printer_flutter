package com.universalprintersearch.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.universalprintersearch.UniversalPrinterSearch
import com.universalprintersearch.model.DiscoveredPrinter
import com.universalprinter.Printer
import com.universalprinter.UniversalPrinter
import com.universalprinter.model.Align
import com.universalprinter.model.Column
import com.universalprinter.model.PaperWidth
import com.universalprinter.model.PrintDocument
import com.universalprinter.model.PrintResult
import com.universalprinter.model.TextSize
import com.universalprinter.model.PrintType
import com.universalprinter.model.PrinterProfiles
import com.universalprinter.model.printDocument
import com.universalprinter.model.textOnly
import com.universalprinter.printReceipt
import android.webkit.WebView
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val discovery = UniversalPrinterSearch(applicationContext)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    DiscoveryScreen(discovery)
                }
            }
        }
    }
}

private fun sampleReceipt(paper: PaperWidth) = printDocument(paper) {
    imageUrl("https://placehold.co/384x120.png", align = Align.CENTER) // logo by URL — cached offline via Glide
    text("UNIVERSAL PRINTER", align = Align.CENTER, bold = true, invert = true, size = TextSize.LARGE)
    text("Sample Receipt", align = Align.CENTER)
    divider()
    columns(Column("Qty", 1, Align.LEFT), Column("Item", 3, Align.LEFT), Column("Amount", 2, Align.RIGHT))
    divider()
    row("1  Coffee", "3.50")
    row("2  Sandwich (extra long name auto-wraps)", "11.00")
    divider()
    row("TOTAL", "14.50")
    feed(1)
    qr("https://example.com/r/123", align = Align.CENTER)
    feed(2)
}

@Composable
private fun DiscoveryScreen(discovery: UniversalPrinterSearch) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Tap a button to search for printers.") }
    var printers by remember { mutableStateOf<List<DiscoveredPrinter>>(emptyList()) }
    var previewHtml by remember { mutableStateOf<String?>(null) }
    var paper by remember { mutableStateOf(PaperWidth.MM_80) }

    // One centralized receipt (sampleReceipt) → printed via the common entry, choosing the print type.
    fun printTo(label: String, type: PrintType, receipt: PrintDocument, factory: () -> Printer) {
        if (loading) return
        scope.launch {
            loading = true
            status = "Printing ($label)…"
            val printer = factory()
            val result = runCatching { printer.printReceipt(context, receipt, type) }
                .getOrElse { PrintResult.Error(it.message ?: "error") }
            printer.close()
            status = when (result) {
                is PrintResult.Success ->
                    "$label: printed ✓" + if (result.warnings.isNotEmpty()) " [${result.warnings.joinToString()}]" else ""
                is PrintResult.Error -> "$label: ${result.reason} — ${result.message}"
            }
            loading = false
        }
    }

    fun run(label: String, block: suspend () -> List<DiscoveredPrinter>) {
        if (loading) return
        scope.launch {
            loading = true
            status = "Searching ($label)…"
            printers = emptyList()
            val result = runCatching { block() }.getOrDefault(emptyList())
            result.forEach {
                android.util.Log.i(
                    "PrinterResult",
                    "[$label] brand=${it.brand} name=\"${it.name}\" ip=${it.ipAddress} mac=${it.macAddress} serial=${it.serialNumber} model=${it.model} emulation=${it.emulation}",
                )
            }
            printers = result
            status = "$label: ${result.size} printer(s) found"
            loading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Universal Printer Search", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "Epson · Sunmi · Zebra · Star (web, SDK-free) · SNMP (Bixolon/Citizen/Brother/Seiko) · Network · USB",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = { run("Epson") { discovery.discoverEpsonPrinters() } }, enabled = !loading) {
                Text("Epson")
            }
            Button(onClick = { run("Sunmi") { discovery.discoverSunmiPrinters() } }, enabled = !loading) {
                Text("Sunmi")
            }
            Button(onClick = { run("Zebra") { discovery.discoverZebraPrinters() } }, enabled = !loading) {
                Text("Zebra")
            }
            Button(onClick = { run("Star") { discovery.discoverStarPrinters() } }, enabled = !loading) {
                Text("Star")
            }
            Button(onClick = { run("SNMP") { discovery.discoverSnmpPrinters() } }, enabled = !loading) {
                Text("SNMP")
            }
            Button(onClick = { run("Network") { discovery.discoverNetworkPrinters() } }, enabled = !loading) {
                Text("Network")
            }
            Button(onClick = { run("USB") { discovery.discoverUsbPrinters() } }, enabled = !loading) {
                Text("USB")
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Paper:", style = MaterialTheme.typography.bodyMedium)
            listOf(PaperWidth.MM_58 to "58mm", PaperWidth.MM_72 to "72mm", PaperWidth.MM_80 to "80mm").forEach { (p, label) ->
                FilterChip(selected = paper == p, onClick = { paper = p }, label = { Text(label) })
            }
            Text("· ${paper.widthPx} dots · ${paper.charsPerLine} chars", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = { printTo("Sunmi", PrintType.TEXT, sampleReceipt(paper)) { UniversalPrinter.sunmi(context) } }, enabled = !loading) {
                Text("Print→Sunmi")
            }
            Button(onClick = { printTo("Net TEXT", PrintType.TEXT, sampleReceipt(paper)) { UniversalPrinter.network("192.168.80.168") } }, enabled = !loading) {
                Text("Net TEXT")
            }
            Button(onClick = { printTo("Net IMAGE", PrintType.IMAGE, sampleReceipt(paper)) { UniversalPrinter.network("192.168.80.168") } }, enabled = !loading) {
                Text("Net IMAGE")
            }
            Button(onClick = {
                if (!loading) scope.launch {
                    loading = true
                    previewHtml = UniversalPrinter.receiptHtml(context, sampleReceipt(paper))
                    loading = false
                }
            }, enabled = !loading) {
                Text("Preview")
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { run("All") { discovery.discoverAll() } },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Discover All")
        }

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (loading) CircularProgressIndicator(modifier = Modifier.size(18.dp))
            Text(status, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(12.dp))

        previewHtml?.let { html ->
            Text("HTML preview (same output as PrintType.IMAGE):", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                    }
                },
                update = { it.loadDataWithBaseURL(null, html, "text/html", "utf-8", null) },
                modifier = Modifier.fillMaxWidth().height(480.dp),
            )
            Spacer(Modifier.height(12.dp))
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(printers) { printer ->
                PrinterCard(printer, canPrint = !loading) { p, type ->
                    // Impact printers can't raster: force TEXT + strip graphics + use their width profile.
                    val profile = PrinterProfiles.forModel(p.model)
                    val effType = if (profile.isImpact) PrintType.TEXT else type
                    val doc = sampleReceipt(profile.paper).let { if (profile.isImpact) it.textOnly() else it }
                    p.ipAddress?.let { ip -> printTo("Test $ip", effType, doc) { UniversalPrinter.network(ip, p.port) } }
                }
            }
        }
    }
}

@Composable
private fun PrinterCard(
    printer: DiscoveredPrinter,
    canPrint: Boolean = false,
    onTest: (DiscoveredPrinter, PrintType) -> Unit = { _, _ -> },
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(printer.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "${printer.connectionType} · ${printer.brand}",
                style = MaterialTheme.typography.labelMedium,
            )
            printer.ipAddress?.let { Line("IP", "$it:${printer.port}") }
            printer.macAddress?.let { Line("MAC", it) }
            printer.serialNumber?.let { Line("Serial", it) }
            printer.model?.let { Line("Model", it) }
            Line("Emulation", printer.effectiveEmulation) // defaults to ESC/POS when unknown
            if (printer.isImpact) {
                val paper = PrinterProfiles.forModel(printer.model).paper
                Line("Type", "IMPACT (9-pin) · text-only · ${paper.charsPerLine} chars")
            }
            if (printer.vendorId != null) {
                Line("USB", "VID ${printer.vendorId} · PID ${printer.productId}")
            }
            if (printer.ipAddress != null) { // test-print to this discovered network printer (at the selected paper size)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onTest(printer, PrintType.TEXT) }, enabled = canPrint) { Text("Test TEXT") }
                    Button(onClick = { onTest(printer, PrintType.IMAGE) }, enabled = canPrint) { Text("Test IMAGE") }
                }
            }
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Text(
        "$label: $value",
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
    )
}
