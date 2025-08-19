// MainActivity.kt

package com.test.nexus

// Core Compose

// Layouts
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.input.pointer.*


// Material 3 components
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.MoreVert
// ... other imports

// ViewModel support for Compose
import MdnsDiscovery
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.test.nexus.ui.theme.NexusTheme
import androidx.lifecycle.viewmodel.compose.viewModel
//import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi

// Network
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.OutputStreamWriter
import java.net.Socket
import android.content.Intent
import androidx.annotation.FloatRange
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import java.util.jar.Attributes.Name
import kotlinx.coroutines.delay
import org.json.JSONObject
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.BufferedWriter
import android.util.Log
import androidx.lifecycle.viewModelScope
import java.util.concurrent.Executors






class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sharedText = intent?.takeIf { it.action == Intent.ACTION_SEND && it.type == "text/plain" }
            ?.getStringExtra(Intent.EXTRA_TEXT)



        setContent {
            NexusTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NexusControlUI(
                        modifier = Modifier.padding(innerPadding),
                        sharedUrl = sharedText
                    )
                }
            }
        }
    }
}


class NexusControlViewModel : ViewModel() {

    private var socket: Socket? = null
    private var writer: BufferedWriter? = null
    val executor = Executors.newSingleThreadExecutor()

    var name by mutableStateOf("")
        private set
    var ip by mutableStateOf("")
        private set
    var url by mutableStateOf("")
        private set
    var port by mutableStateOf(0)
        private set


    var isConnected = false
        private set

    fun connectSocket(){
        executor.execute {
            try {
                socket = Socket(ip, port)
                writer = BufferedWriter(OutputStreamWriter(socket!!.getOutputStream()))
                Log.d("Socket", "Connection sucesseful")
                isConnected = true
            } catch (e: Exception) {
                Log.e("Socket", "Connection failed: $e")
                writer = null
                socket = null
                isConnected = false
            }
        }
    }

    fun closeSocket() {
        executor.execute {
            try {
                writer?.close()
                socket?.close()
                writer = null
                socket = null
                isConnected = false
                Log.d("Socket", "Close successful")
            } catch (e: Exception) {
                Log.e("Socket", "Close failed: ${e.message}", e)
            }
        }
    }

    private fun sendMessage(map: Map<String, Any>) {
        executor.execute {
            try {
                val json = JSONObject(map).toString()
                writer?.apply {
                    write(json)
                    newLine()
                    flush()
                }
                Log.d("Send", "Sent message: $json")
            } catch (e: Exception) {
                Log.e("Socket", "Send failed: $e")
            }
        }
    }



    fun updatePort(newPort: Int){
        port = newPort
    }
    fun updateIp(newIp: String){
        ip = newIp
    }
    fun updateUrl(newUrl: String){
        url = newUrl
    }
    fun updateName(newName: String){
        name = newName
    }
    fun sendUrl() {
        //if (writer == null) {
        //    val connected = connectSocket()
        //    if (!connected) {
         //       Log.e("Socket", "Could not connect to send URL")
         //       return
          //  }

        sendMessage(mapOf("type" to "cast_url", "url" to url))
    }

    fun sendMove(dx: Float, dy: Float) {
        sendMessage(mapOf("type" to "move", "dx" to dx, "dy" to dy))
    }

    fun sendClick(button: String) {
        sendMessage(mapOf("type" to "click", "button" to button))
    }

    fun sendScroll(dy: Float) {
        sendMessage(mapOf("type" to "scroll", "dy" to dy))
    }
    override fun onCleared() {
        super.onCleared()
        closeSocket()
    }
}

@Composable
fun HostDropDownMenu(discovery : MdnsDiscovery, viewModel: NexusControlViewModel){
    var expanded by remember { mutableStateOf(false)}
    val hosts = remember { mutableStateListOf<Pair<Pair<String, Int>,String>>() }

    // Collect discovered hosts
    LaunchedEffect(discovery) {
        discovery.discoveryServices { host, port, hostname ->
            val newEntry = (host to port) to (hostname ?: "Unnamed Device")
            if (!hosts.any { it.first == (host to port) }) {
                hosts.add(newEntry)
                // If it's the only host, auto-select it
                // Auto-select the first/only host
                if (hosts.size == 1) {
                    val (selectedHost, selectedPort) = hosts[0].first
                    val selectedName = hosts[0].second
                    viewModel.apply {
                        updateIp(selectedHost)
                        updatePort(selectedPort)
                        updateName(selectedName)
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.padding(16.dp)){

        IconButton(onClick = { expanded = !expanded }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Hosts"
            )

        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ){
            if (hosts.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No hosts found") },
                    onClick = { expanded = false }
                )
            } else {
                hosts.forEach { (hostPort, name) ->  // Destructure both hostPort and name
                    val (host, port) = hostPort  // Unpack the host and port
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = name,
                                    //style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "$host:$port",
                                    //style = MaterialTheme.typography.bodySmall,
                                    //color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = {
                            viewModel.updateIp(host)
                            viewModel.updatePort(port)
                            viewModel.updateName(name)
                            expanded = false
                        }
                    )
                }
            }
        }
    }

}

@Composable
fun TouchpadArea(viewModel: NexusControlViewModel, modifier: Modifier = Modifier) {
    var lastX by remember { mutableStateOf(0f) }
    var lastY by remember { mutableStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.6f)
            .background(Color.Gray.copy(alpha = 0.2f))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val touches = event.changes

                        if (touches.size == 1) {
                            val pointer = touches[0]
                            if (pointer.changedToDown()) {
                                lastX = pointer.position.x
                                lastY = pointer.position.y
                            }
                            if (pointer.pressed) {
                                val dx = pointer.position.x - lastX
                                val dy = pointer.position.y - lastY
                                lastX = pointer.position.x
                                lastY = pointer.position.y
                                viewModel.sendMove(dx, dy)
                            }
                            if (pointer.changedToUp()) {
                                viewModel.sendClick("left")
                            }
                        } else if (touches.size == 2) {
                            val pointer1 = touches[0]
                            val pointer2 = touches[1]

                            if (pointer1.changedToUp() && pointer2.changedToUp()) {
                                viewModel.sendClick("right")
                            } else if (pointer1.pressed && pointer2.pressed) {
                                val dy = (pointer1.positionChange().y + pointer2.positionChange().y) / 2
                                viewModel.sendScroll(dy)
                            }
                        }
                    }
                }
            }
    )
}

// Transformation class
class SmartPrefixTransformation(
    private val prefixProvider: () -> String // Dynamic prefix getter
) : VisualTransformation
{
    override fun filter(text: AnnotatedString): TransformedText {
        val prefix = prefixProvider()
        return if (prefix.isEmpty() || text.text.startsWith(prefix)) {
            TransformedText(text, OffsetMapping.Identity)
        } else {
            TransformedText(
                text = AnnotatedString(prefix + text.text),
                offsetMapping = object : OffsetMapping {
                    override fun originalToTransformed(offset: Int) =
                        (offset + prefix.length).coerceAtLeast(0)
                    override fun transformedToOriginal(offset: Int) =
                        (offset - prefix.length).coerceAtLeast(0)
                }
            )
        }
    }
}



@OptIn(UnstableApi::class)
@Composable
fun NexusControlUI(
    viewModel: NexusControlViewModel = viewModel(),
    modifier: Modifier = Modifier,
    sharedUrl: String? = null,
) {
    val ip = viewModel.ip
    val url = viewModel.url
    val name = viewModel.name
    val discovery = MdnsDiscovery(LocalContext.current).apply {  }



    Column(modifier = Modifier
        .padding(16.dp)
        .fillMaxSize(),
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
            ) {
            TextField(
                value = ip,
                onValueChange = { viewModel.updateIp(it) },
                label = { Text("Device IP") },
                visualTransformation = SmartPrefixTransformation {
                    if (viewModel.name.isNullOrEmpty()) "" else "${viewModel.name}: "
                }

            )
            Spacer (modifier = Modifier.width(8.dp))
            HostDropDownMenu(discovery, viewModel)
            }
        Spacer(
            modifier = Modifier.height(8.dp)
        )
        TextField(
            value = url,
            onValueChange = { viewModel.updateUrl(it) },
            label = { Text("URL to Cast") }
        )
        Spacer(
            modifier = Modifier.height(16.dp)
        )
        // Button(onClick = {
        //     viewModel.sendUrl()
        // }) {
        //     Text("Cast URL")
        //}
        TouchpadArea(viewModel, modifier = Modifier.fillMaxWidth().height(250.dp))
        LaunchedEffect(sharedUrl) {


            sharedUrl?.let { url ->

                discovery.discoveryServices{host,port,name ->
                    Log.d("NSD", "Found $name Server at $host:$port")
                    viewModel.updateIp(host)
                    viewModel.updateName(name)
                    viewModel.updatePort(port)
                }
                // 1. Update the URL in ViewModel


                // 2. Wait briefly to ensure state updates propagate
                delay(100) // Small delay for state consistency

                // 3. Validate we have a target device
                if (viewModel.ip.isNotEmpty() && viewModel.port > 0) {
                    // 4. Send with error handling
                    //viewModelScope.launch(Dispatchers.IO) {
                        try {
                            //viewModel.closeSocket()

                            if(!viewModel.isConnected){
                                viewModel.connectSocket()
                            }
                            viewModel.updateUrl(url)
                            viewModel.sendUrl()

                            Log.d("AutoCast", "Successfully auto-casted URL")
                        } catch (e: Exception) {
                            Log.e("AutoCast", "Failed to auto-cast URL", e)
                        }
                } else {
                    Log.w("AutoCast", "No target device available for auto-casting")
                }
            }
        }
    }

}




@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    NexusTheme {
        NexusControlUI()
    }
}