package com.sirdella.vaimpremote

import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.*
import kotlin.collections.ArrayList


class IpSelectionActivity : AppCompatActivity() {
    lateinit var localIp: String
    var listaIps = ArrayList<IpListDC>()
    var cantIpsCheckeadas = 0

    val logger = Logger(this)

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ip_selection)
        logger.log("Inicio IpSelectionActivity", "Flowlog")

        val servicioVaimpIp = VaimpCalls()
        val app = (application as App)

        //Recycler:
        rvLista = findViewById<RecyclerView>(R.id.recyclerCanciones)
        adapterRecycler = ipAdapter(this, callbackClick = {ip->
            //ocultarTeclado()
            servicioVaimpIp.isVAIMP(ip.address){
                if (it)
                {
                    app.repoVaimp!!.mainIp = ip.address
                    app.repoVaimp!!.actualizarIp()
                    super.finish()
                }
            }
        })
        rvLista.adapter = adapterRecycler

        //SwipeRefreshLayout:
        val refreshLayout = findViewById<SwipeRefreshLayout>(R.id.refreshLayout)
        refreshLayout.setOnRefreshListener {
            busquedaIps(servicioVaimpIp, refreshLayout, app)
        }
        refreshLayout.setColorSchemeColors(this.getColor(R.color.vaimpGreen))
        refreshLayout.setProgressBackgroundColorSchemeColor(this.getColor(R.color.fondoOscuro2))

        busquedaIps(servicioVaimpIp, refreshLayout, app)
        app.repoVaimp!!.iniciarJsonService()

        val etIp = findViewById<EditText>(R.id.etIp)
        etIp.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                servicioVaimpIp.isVAIMP(p0.toString()){
                    if (it)
                    {
                        app.repoVaimp!!.mainIp = p0.toString()
                        app.repoVaimp!!.actualizarIp()
                        app.repoVaimp!!.agregarIp(p0.toString())
                        this@IpSelectionActivity.finish()
                    }
                }
            }

            override fun afterTextChanged(p0: Editable?) {
            }
        })

        var timer = Timer().scheduleAtFixedRate(object : TimerTask() {
            override fun run() {

            }
        }, 0, 1000)

        logger.log("Fin IpSelectionActivity", "Flowlog")
    }

    private fun busquedaIps(servicioVaimpIp: VaimpCalls, refresh: SwipeRefreshLayout, app: App){
        listaIps = ArrayList()
        adapterRecycler.actualizarLista(listaIps)
        for(i in app.repoVaimp!!.ips)
        {
            listaIps.add(i)
        }

        for(i in listaIps)
        {
            servicioVaimpIp.isVAIMP(i.address) {
                i.online = it
                runOnUiThread { adapterRecycler.actualizarLista(listaIps) }
            }
        }

        refresh.isRefreshing = true
        localIp = getIPHostAddress()
        cantIpsCheckeadas = 0
        val ipSplit = localIp.split(".")
        var ipWithoutLast = ""
        var i = 0
        while (i < ipSplit.size - 1) {
            ipWithoutLast += ipSplit[i] + "."
            i++
        }

        for (i in 0..255)
        {
            llamarIp(servicioVaimpIp, ipWithoutLast, i, refresh)
        }
    }

    private fun llamarIp(
        servicioVaimpIp: VaimpCalls,
        ipWithoutLast: String,
        i: Int,
        refresh: SwipeRefreshLayout
    ) {
        GlobalScope.launch(Dispatchers.IO) {
            servicioVaimpIp.isVAIMPnoAsync(ipWithoutLast + i + ":5045", {
                if (it) {
                    val ip = IpListDC(ipWithoutLast + i + ":5045", true)
                    listaIps.add(ip)
                    runOnUiThread { adapterRecycler.actualizarLista(listaIps) }
                }
            }, 1000)
            if (i == 255) {
                runOnUiThread { refresh.isRefreshing = false }
            }
        }
    }

    fun getIPHostAddress(): String {
        NetworkInterface.getNetworkInterfaces()?.toList()?.map { networkInterface ->
            networkInterface.inetAddresses?.toList()?.find {
                !it.isLoopbackAddress && it is Inet4Address
            }?.let { return it.hostAddress }
        }
        return ""
    }

    lateinit var adapterRecycler: ipAdapter
    lateinit var rvLista: RecyclerView

    class ipAdapter(private val contexto: Context, private val callbackClick: (IpListDC) -> Unit) : RecyclerView.Adapter<ipVH>() {

        var ips = listOf<IpListDC>()

        fun actualizarLista(nuevaLista: List<IpListDC>){
            ips = nuevaLista
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ipVH {
            val view = LayoutInflater
                .from(parent.context)
                .inflate(R.layout.item_ip, parent, false)
            return ipVH(view)
        }

        @RequiresApi(Build.VERSION_CODES.M)
        override fun onBindViewHolder(holder: ipVH, position: Int) {
            val ip = ips[position]

            holder.tvNombre.text = ip.address
            if (ip.online)
            {
                holder.cView.setCardBackgroundColor(contexto.getColor(R.color.ipGreen))
            }
            else
            {
                holder.cView.setCardBackgroundColor(contexto.getColor(R.color.ipGray))
            }

            holder.itemView.setOnClickListener(object : View.OnClickListener {
                override fun onClick(v: View?) {
                    callbackClick(ip)
                }
            })
        }

        override fun getItemCount(): Int {
            return ips.size
        }
    }

    class ipVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvNombre = itemView.findViewById<TextView>(R.id.textViewCancion)
        val cView = itemView.findViewById<CardView>(R.id.cardViewLista)
    }
}