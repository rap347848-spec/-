package com.talal.netcard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var preview: PreviewView
    private lateinit var etUser: EditText
    private lateinit var etPass: EditText
    private lateinit var tvStatus: TextView
    private lateinit var webView: WebView
    private lateinit var overlay: TextView
    private lateinit var btnFlash: TextView
    private lateinit var btnShowPage: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val executor = Executors.newSingleThreadExecutor()

    private var camera: Camera? = null
    private var torchOn = true
    @Volatile private var busy = false
    @Volatile private var lastAnalyze = 0L
    private var lastKey: String? = null
    private var stable = 0
    private var emptyCount = 0
    private var doneKey: String? = null
    private var session = 0
    private var pendingUser = ""
    private var pendingPass = ""
    private var injects = 0
    private var wifiNet: Network? = null
    private var pageVisible = false

    private val probeUrl = "http://connectivitycheck.gstatic.com/generate_204"

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            if (ok) startCamera() else setStatus("يلزم السماح للكاميرا لالتقاط الأرقام")
        }

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                try {
                    val img = InputImage.fromFilePath(this, uri)
                    recognizer.process(img).addOnSuccessListener { handleText(it.text, true) }
                } catch (e: Exception) {
                    Toast.makeText(this, "تعذّر فتح الصورة", Toast.LENGTH_SHORT).show()
                }
            }
        }

    private val savedLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            if (r.resultCode == RESULT_OK) {
                val u = r.data?.getStringExtra("user") ?: return@registerForActivityResult
                val p = r.data?.getStringExtra("pass") ?: return@registerForActivityResult
                etUser.setText(u)
                etPass.setText(p)
                doneKey = null
                startLogin(u, p)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        setContentView(R.layout.activity_main)

        preview = findViewById(R.id.preview)
        preview.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        etUser = findViewById(R.id.etUser)
        etPass = findViewById(R.id.etPass)
        tvStatus = findViewById(R.id.tvStatus)
        webView = findViewById(R.id.webView)
        overlay = findViewById(R.id.overlay)
        btnFlash = findViewById(R.id.btnFlash)
        btnShowPage = findViewById(R.id.btnShowPage)

        setupWebView()

        findViewById<View>(R.id.btnSaved).setOnClickListener {
            savedLauncher.launch(Intent(this, SavedCardsActivity::class.java))
        }
        findViewById<View>(R.id.btnGallery).setOnClickListener { pickImage.launch("image/*") }
        findViewById<View>(R.id.btnLogin).setOnClickListener {
            val u = etUser.text.toString().trim()
            val p = etPass.text.toString().trim()
            if (u.isEmpty()) {
                Toast.makeText(this, "أدخل اسم المستخدم", Toast.LENGTH_SHORT).show()
            } else {
                doneKey = null
                startLogin(u, p)
            }
        }
        btnFlash.setOnClickListener {
            torchOn = !torchOn
            camera?.cameraControl?.enableTorch(torchOn)
            btnFlash.text = if (torchOn) "الفلاش: يعمل" else "الفلاش: مطفأ"
        }
        btnShowPage.setOnClickListener {
            pageVisible = !pageVisible
            val lp = webView.layoutParams
            lp.height = if (pageVisible) (320 * resources.displayMetrics.density).toInt() else 1
            webView.layoutParams = lp
            btnShowPage.text = if (pageVisible) "إخفاء صفحة الدخول" else "عرض صفحة الدخول"
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) startCamera() else permLauncher.launch(Manifest.permission.CAMERA)
    }

    // ---------------- الكاميرا والتعرف على الأرقام ----------------

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val pv = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { proxy -> analyze(proxy) }
            provider.unbindAll()
            camera = provider.bindToLifecycle(
                this, CameraSelector.DEFAULT_BACK_CAMERA, pv, analysis
            )
            camera?.cameraControl?.enableTorch(torchOn)
        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun analyze(proxy: ImageProxy) {
        val media = proxy.image
        val now = SystemClock.elapsedRealtime()
        if (media == null || busy || now - lastAnalyze < 350) {
            proxy.close()
            return
        }
        lastAnalyze = now
        val img = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
        recognizer.process(img)
            .addOnSuccessListener { handleText(it.text, false) }
            .addOnCompleteListener { proxy.close() }
    }

    private fun normalize(s: String): String {
        val sb = StringBuilder()
        for (ch in s) {
            when (ch) {
                in '\u0660'..'\u0669' -> sb.append('0' + (ch - '\u0660'))
                in '\u06F0'..'\u06F9' -> sb.append('0' + (ch - '\u06F0'))
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /** الرقم الأطول = اسم المستخدم، والأقصر الذي يليه = كلمة السر */
    private fun extractPair(raw: String): Pair<String, String>? {
        val cands = mutableListOf<String>()
        for (line in normalize(raw).lines()) {
            val compact = line.replace(Regex("[\\s\\-]"), "")
            if (compact.length >= 4 && compact.all { it.isDigit() }) {
                cands.add(compact)
            } else {
                Regex("\\d{4,}").findAll(line).forEach { cands.add(it.value) }
            }
        }
        if (cands.size < 2) return null
        val user = cands.filter { it.length >= 7 }.maxByOrNull { it.length } ?: return null
        val others = cands.filter { it != user && it.length in 4..12 && it.length < user.length }
        if (others.isEmpty()) return null
        val after = cands.drop(cands.indexOf(user) + 1).firstOrNull { it in others }
        return user to (after ?: others.minByOrNull { it.length }!!)
    }

    private fun handleText(text: String, force: Boolean) {
        val pair = extractPair(text)
        if (pair == null) {
            stable = 0
            lastKey = null
            emptyCount++
            if (emptyCount >= 6) doneKey = null
            if (force) Toast.makeText(this, "لم أجد رقمين في الصورة", Toast.LENGTH_SHORT).show()
            return
        }
        emptyCount = 0
        if (busy) return
        val u = pair.first
        val p = pair.second
        val key = "$u|$p"
        if (!force) {
            if (key == doneKey) return
            if (key == lastKey) stable++ else { lastKey = key; stable = 1 }
            if (stable < 2) return
        }
        etUser.setText(u)
        etPass.setText(p)
        doneKey = key
        startLogin(u, p)
    }

    // ---------------- الشبكة وتسجيل الدخول ----------------

    @Suppress("DEPRECATION")
    private fun bindWifi(): Network? {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val n = cm.allNetworks.firstOrNull {
            cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
        if (n != null) cm.bindProcessToNetwork(n)
        wifiNet = n
        return n
    }

    private fun isOnline(net: Network?): Boolean = try {
        val url = URL(probeUrl)
        val c = (net?.openConnection(url) ?: url.openConnection()) as HttpURLConnection
        c.instanceFollowRedirects = false
        c.connectTimeout = 4000
        c.readTimeout = 4000
        c.useCaches = false
        val code = c.responseCode
        c.disconnect()
        code == 204
    } catch (e: Exception) {
        false
    }

    private fun startLogin(u: String, p: String) {
        if (busy) return
        busy = true
        val my = ++session
        pendingUser = u
        pendingPass = p
        injects = 0
        setStatus("تم التقاط الرقمين... جارٍ الدخول")
        val net = bindWifi()
        if (net == null) {
            finishFail("اتصل بشبكة واي فاي مفتوحة أولاً")
            return
        }
        Thread {
            val online = isOnline(net)
            runOnUiThread {
                if (my != session) return@runOnUiThread
                if (online) {
                    busy = false
                    setStatus("أنت متصل بالإنترنت فعلاً")
                } else {
                    webView.loadUrl(probeUrl)
                    poll(my, 0)
                }
            }
        }.start()
    }

    private fun poll(my: Int, n: Int) {
        handler.postDelayed({
            if (my != session || !busy) return@postDelayed
            Thread {
                val ok = isOnline(wifiNet)
                runOnUiThread {
                    if (my != session || !busy) return@runOnUiThread
                    when {
                        ok -> onSuccess()
                        n >= 11 -> finishFail("تعذّر الدخول، تأكد من الكرت أو من الشبكة")
                        else -> poll(my, n + 1)
                    }
                }
            }.start()
        }, 2500)
    }

    private fun onSuccess() {
        busy = false
        CardStore.add(this, pendingUser, pendingPass)
        setStatus("تم الدخول بنجاح وحُفظ الكرت")
        overlay.visibility = View.VISIBLE
        handler.postDelayed({ overlay.visibility = View.GONE }, 5000)
    }

    private fun finishFail(msg: String) {
        busy = false
        setStatus(msg)
    }

    private fun setStatus(s: String) {
        tvStatus.text = s
    }

    private fun setupWebView() {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(v: WebView?, url: String?) {
                if (!busy || injects >= 3 || pendingUser.isEmpty()) return
                val my = session
                handler.postDelayed({
                    if (my == session && busy && injects < 3) inject()
                }, 700)
            }
        }
    }

    private fun inject() {
        val js = """
            (function(u,p){
              try{
                function setv(el,v){
                  var d=Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype,'value');
                  if(d&&d.set){d.set.call(el,v);}else{el.value=v;}
                  el.dispatchEvent(new Event('input',{bubbles:true}));
                  el.dispatchEvent(new Event('change',{bubbles:true}));
                }
                var ok=['text','tel','number','password','email',''];
                var vis=[].slice.call(document.querySelectorAll('input')).filter(function(i){
                  var t=(i.getAttribute('type')||'text').toLowerCase();
                  return ok.indexOf(t)>=0 && !i.disabled && window.getComputedStyle(i).display!=='none';
                });
                var pw=vis.filter(function(i){return i.type==='password';})[0];
                var us=vis.filter(function(i){return i.type!=='password';})[0];
                if(!pw&&!us)return 'nofields';
                if(us)setv(us,u);
                if(pw)setv(pw,p);
                var form=(us||pw).form;
                var sel='button[type=submit],input[type=submit],input[type=button],button';
                var btn=(form&&form.querySelector(sel))||document.querySelector(sel);
                if(btn){btn.click();}else if(form){form.submit();}else{return 'nosubmit';}
                return 'submitted';
              }catch(e){return 'error';}
            })(__U__,__P__)
        """.trimIndent()
            .replace("__U__", JSONObject.quote(pendingUser))
            .replace("__P__", JSONObject.quote(pendingPass))
        webView.evaluateJavascript(js) { r ->
            if (r != null && r.contains("submitted")) injects++
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        executor.shutdown()
        recognizer.close()
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
    }
}
