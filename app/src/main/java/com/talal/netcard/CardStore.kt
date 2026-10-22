package com.talal.netcard

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object CardStore {
    data class Card(val user: String, val pass: String, val time: Long)

    private fun prefs(c: Context) = c.getSharedPreferences("cards", Context.MODE_PRIVATE)

    fun all(c: Context): MutableList<Card> {
        val arr = JSONArray(prefs(c).getString("list", "[]"))
        val out = mutableListOf<Card>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(Card(o.getString("u"), o.getString("p"), o.getLong("t")))
        }
        return out
    }

    private fun write(c: Context, list: List<Card>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("u", it.user).put("p", it.pass).put("t", it.time))
        }
        prefs(c).edit().putString("list", arr.toString()).apply()
    }

    fun add(c: Context, user: String, pass: String) {
        val l = all(c)
        l.removeAll { it.user == user }
        l.add(0, Card(user, pass, System.currentTimeMillis()))
        write(c, l)
    }

    fun remove(c: Context, user: String) {
        val l = all(c)
        l.removeAll { it.user == user }
        write(c, l)
    }
}
