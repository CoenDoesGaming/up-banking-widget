package au.com.up.lockwidget

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvAccountLabel: TextView
    private lateinit var tvBalanceValue: TextView
    private lateinit var tvActivityTx: TextView
    private lateinit var tvSaverBalance: TextView
    private lateinit var tvSaverTx: TextView
    private lateinit var spinnerSavers: Spinner
    private lateinit var tvCategorySummary: TextView
    private lateinit var spinnerCategory: Spinner
    private lateinit var etCategoryBudget: EditText
    private lateinit var etToken: EditText
    private lateinit var btnSaveSync: Button

    private var saverOptions: List<UpPreferences.SaverOption> = emptyList()
    private val categoryOptions = UpPreferences.DEFAULT_CATEGORIES

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tv_status)
        tvAccountLabel = findViewById(R.id.tv_account_label)
        tvBalanceValue = findViewById(R.id.tv_balance_value)
        tvActivityTx = findViewById(R.id.tv_activity_tx)
        tvSaverBalance = findViewById(R.id.tv_saver_balance)
        tvSaverTx = findViewById(R.id.tv_saver_tx)
        spinnerSavers = findViewById(R.id.spinner_savers)
        tvCategorySummary = findViewById(R.id.tv_category_summary)
        spinnerCategory = findViewById(R.id.spinner_category)
        etCategoryBudget = findViewById(R.id.et_category_budget)
        etToken = findViewById(R.id.et_token)
        btnSaveSync = findViewById(R.id.btn_save_sync)
        val btnInstantNotif: Button = findViewById(R.id.btn_instant_notif)

        AutoSyncScheduler.ensureScheduled(this)

        val currentToken = UpPreferences.getToken(this)
        etToken.setText(currentToken)
        etCategoryBudget.setText(UpPreferences.getCategoryBudget(this).toString())

        setupCategorySpinner()
        setupSaversSpinner()
        renderCachedState()

        btnSaveSync.setOnClickListener {
            saveInputsAndSync()
        }

        btnInstantNotif.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        if (currentToken.isNotEmpty()) {
            syncNow()
        }
    }

    private fun setupCategorySpinner() {
        val names = categoryOptions.map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategory.adapter = adapter

        val currentCatId = UpPreferences.getCategoryId(this)
        val idx = categoryOptions.indexOfFirst { it.id == currentCatId }.coerceAtLeast(0)
        spinnerCategory.setSelection(idx, false)

        spinnerCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                (view as? TextView)?.setTextColor(0xFFFFFFFF.toInt())
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupSaversSpinner() {
        saverOptions = UpPreferences.getSaversOptions(this)
        val names = saverOptions.map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSavers.adapter = adapter

        val selectedId = UpPreferences.getSelectedSaverId(this)
        val idx = saverOptions.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
        spinnerSavers.setSelection(idx, false)

        spinnerSavers.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                (view as? TextView)?.setTextColor(0xFFFFFFFF.toInt())
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun saveInputsAndSync() {
        val inputToken = etToken.text.toString().trim()
        if (inputToken.isEmpty()) {
            Toast.makeText(this, "Please enter your Up Personal Access Token", Toast.LENGTH_SHORT).show()
            return
        }
        UpPreferences.setToken(this, inputToken)

        val budgetVal = etCategoryBudget.text.toString().trim().toIntOrNull() ?: 200
        UpPreferences.setCategoryBudget(this, budgetVal)

        val catIndex = spinnerCategory.selectedItemPosition.coerceIn(categoryOptions.indices)
        val chosenCat = categoryOptions[catIndex]
        UpPreferences.setCategorySelection(this, chosenCat.id, chosenCat.name)

        if (saverOptions.isNotEmpty()) {
            val saverIndex = spinnerSavers.selectedItemPosition.coerceIn(saverOptions.indices)
            UpPreferences.setSelectedSaverId(this, saverOptions[saverIndex].id)
        }

        syncNow()
    }

    private fun renderCachedState() {
        tvStatus.text = UpPreferences.getLastUpdated(this)
        tvAccountLabel.text = "ACTIVITY • ${UpPreferences.getAccountName(this).uppercase()}"
        tvBalanceValue.text = UpPreferences.getBalanceFull(this)
        val actAmount = UpPreferences.getActivityTxAmount(this)
        tvActivityTx.text = if (actAmount.isNotEmpty() && actAmount != "—") {
            "${UpPreferences.getActivityTxDesc(this)} • $actAmount"
        } else {
            UpPreferences.getActivityTxDesc(this)
        }

        tvSaverBalance.text = "${UpPreferences.getSaverTitle(this)}: ${UpPreferences.getSaverBalanceWhole(this)}"
        tvSaverTx.text = "${UpPreferences.getSaverTxDesc(this)} • ${UpPreferences.getSaverTxAmount(this)}"

        tvCategorySummary.text =
            "${UpPreferences.getCategoryName(this)} (${UpPreferences.getCategoryCode(this)}) • ${UpPreferences.getCategoryLeftLabel(this)}"
    }

    private fun syncNow() {
        tvStatus.text = "Syncing with Up API..."
        btnSaveSync.isEnabled = false

        WidgetUpdateHelper.refreshFromApiAsync(this) { result ->
            runOnUiThread {
                btnSaveSync.isEnabled = true
                result.onSuccess {
                    setupSaversSpinner()
                    renderCachedState()
                    Toast.makeText(this, "All Lock Screen widgets updated!", Toast.LENGTH_SHORT).show()
                }.onFailure { err ->
                    tvStatus.text = "Sync failed: ${err.message}"
                }
            }
        }
    }
}
