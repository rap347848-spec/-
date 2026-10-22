package com.talal.netcard

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SavedCardsActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var empty: TextView
    private var cards = listOf<CardStore.Card>()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_main)
            setPadding(dp(16), dp(24), dp(16), dp(16))
        }
        root.addView(TextView(this).apply {
            text = "البطاقات المحفوظة"
            textSize = 22f
            setTextColor(0xFF0B4F85.toInt())
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "اضغط على بطاقة للدخول بها، واضغط مطولاً للحذف"
            textSize = 13f
            setTextColor(0xFF33648A.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(10))
        })
        empty = TextView(this).apply {
            text = "لا توجد بطاقات محفوظة بعد"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(0xFF0B4F85.toInt())
            setPadding(0, dp(40), 0, 0)
        }
        listView = ListView(this)
        root.addView(listView, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(empty)
        setContentView(root)

        listView.setOnItemClickListener { _, _, pos, _ ->
            val c = cards[pos]
            setResult(RESULT_OK, Intent().putExtra("user", c.user).putExtra("pass", c.pass))
            finish()
        }
        listView.setOnItemLongClickListener { _, _, pos, _ ->
            val c = cards[pos]
            AlertDialog.Builder(this)
                .setMessage("حذف هذه البطاقة؟\n${c.user}")
                .setPositiveButton("حذف") { _, _ -> CardStore.remove(this, c.user); refresh() }
                .setNegativeButton("إلغاء", null)
                .show()
            true
        }
        refresh()
    }

    private fun refresh() {
        cards = CardStore.all(this)
        val fmt = SimpleDateFormat("yyyy/MM/dd  HH:mm", Locale.US)
        val rows = cards.map {
            "${it.user}\nكلمة السر: ${it.pass}\n${fmt.format(Date(it.time))}"
        }
        listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, rows)
        empty.visibility = if (cards.isEmpty()) View.VISIBLE else View.GONE
    }
}
