package com.lisica.certinstaller

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CertificateListActivity : AppCompatActivity() {

    private lateinit var keyPairContainer: LinearLayout
    private lateinit var caCertContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_certificate_list)

        // Set up toolbar
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Set navigation icon color to white
        toolbar.navigationIcon?.setTint(getColor(android.R.color.white))

        keyPairContainer = findViewById(R.id.keyPairContainer)
        caCertContainer = findViewById(R.id.caCertContainer)

        loadCertificates()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadCertificates() {
        lifecycleScope.launch(Dispatchers.IO) {
            // Load key pairs
            val keyPairResult = CertificateInstaller.getInstalledKeyPairDetails(this@CertificateListActivity)
            val keyPairDetails = if (keyPairResult.isSuccess) {
                keyPairResult.getOrThrow()
            } else {
                emptyList()
            }

            // Load CA certs
            val caCertResult = CertificateInstaller.getInstalledCaCertDetails(this@CertificateListActivity)
            val caCertDetails = if (caCertResult.isSuccess) {
                caCertResult.getOrThrow()
            } else {
                emptyList()
            }

            withContext(Dispatchers.Main) {
                displayKeyPairs(keyPairDetails)
                displayCaCerts(caCertDetails)
            }
        }
    }

    private fun displayKeyPairs(details: List<String>) {
        keyPairContainer.removeAllViews()

        if (details.isEmpty() || (details.size == 1 && details[0].contains("No key pairs found"))) {
            val textView = TextView(this)
            textView.text = "No key pairs installed"
            textView.setTextColor(getColor(R.color.gray))
            textView.textSize = 14f
            textView.setPadding(0, 16, 0, 16)
            keyPairContainer.addView(textView)
            return
        }

        details.forEachIndexed { index, detail ->
            addCertificateView(keyPairContainer, detail)

            // Add divider between items
            if (index < details.size - 1) {
                addDivider(keyPairContainer)
            }
        }
    }

    private fun displayCaCerts(details: List<String>) {
        caCertContainer.removeAllViews()

        if (details.isEmpty()) {
            val textView = TextView(this)
            textView.text = "No CA certificates installed"
            textView.setTextColor(getColor(R.color.gray))
            textView.textSize = 14f
            textView.setPadding(0, 16, 0, 16)
            caCertContainer.addView(textView)
            return
        }

        details.forEachIndexed { index, detail ->
            addCaCertView(caCertContainer, detail)

            // Add divider between items
            if (index < details.size - 1) {
                addDivider(caCertContainer)
            }
        }
    }

    private fun addCertificateView(container: LinearLayout, detail: String) {
        val textView = TextView(this)
        textView.text = detail
        textView.setTextColor(getColor(android.R.color.black))
        textView.textSize = 12f
        textView.typeface = Typeface.MONOSPACE
        textView.setPadding(0, 16, 0, 16)
        container.addView(textView)
    }

    private fun addCaCertView(container: LinearLayout, detail: String) {
        val textView = TextView(this)
        textView.text = detail
        textView.setTextColor(getColor(android.R.color.black))
        textView.textSize = 12f
        textView.typeface = Typeface.MONOSPACE
        textView.setPadding(0, 16, 0, 16)
        container.addView(textView)
    }

    private fun addDivider(container: LinearLayout) {
        val divider = View(this)
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            1
        )
        divider.layoutParams = params
        divider.setBackgroundColor(getColor(R.color.gray))
        container.addView(divider)
    }
}
