package com.example.finance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import androidx.navigation.navArgument
import com.example.finance.ui.theme.FinanceTheme
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FinanceTheme {
                FinanceApp()
            }
        }
    }
}

data class FinanceGoal(
    var name: String = "",
    var downPayment: Double = 0.0,
    //var startDate: LocalDate = LocalDate.now(),
    var monthlySavings: Double = 0.0
)


data class IncomeEntry(
    val date: String = "",
    val type: String = "",
    val amount: Double = 0.0,
    val description: String = "",
    val key: String? = null
) {
    fun getLocalDate(): LocalDate {
        return LocalDate.parse(date, DateTimeFormatter.ISO_DATE)
    }
}

data class ExpenseEntry(
    val date: String = "",
    val type: String = "",
    val amount: Double = 0.0,
    val description: String = "",
    val key: String? = null
) {
    fun getLocalDate(): LocalDate {
        return LocalDate.parse(date, DateTimeFormatter.ISO_DATE)
    }
}

@Composable
fun FinanceApp(){
    val navController = rememberNavController()
    val viewModel: FinanceViewModel = viewModel()
    val registrationViewModel: RegistrationViewModel = viewModel()

    NavHost(navController, startDestination = "home") {
        composable("landing") {
            LandingPage(
                onRegisterClick = { navController.navigate("register") },
                onSignInClick = { navController.navigate("signIn") }
            )
        }
        composable("signIn") {
            SignInPage(registrationViewModel,
                onSignInSuccess = { navController.navigate("home") },
                onRegisterClick = { navController.navigate("register") }
            )
        }
        composable("register") {
            RegistrationPage(
                registrationViewModel,
                onCancelClick = { navController.navigate("landing") }
            )
        }
        composable("home") {
            HomeScreen(
                viewModel,
                onAddNewGoalClick = { navController.navigate("addGoal") },
                onViewIncomeClick = { navController.navigate("monthlyIncome") },
                onViewExpenseClick = { navController.navigate("monthlyExpense") },
                onSignOutClick = { navController.navigate("landing") },
                onAddExpenseClick = { navController.navigate("addExpense?key={key}") },
                onAddIncomeClick = { navController.navigate("addIncome?key={key}") }
            )
        }

        composable("addGoal") {
            AddGoalScreen(
                viewModel,
                onCancel = { navController.popBackStack() }
            )
        }
        composable(
            route = "addIncome?key={key}",
            arguments = listOf(navArgument("key") { nullable = true })
        ) { backStackEntry ->
            val incomeKey = backStackEntry.arguments?.getString("key")
            AddIncomeScreen(
                viewModel = viewModel,
                onCancel = { navController.popBackStack() },
                incomeKey = incomeKey
            )
        }
        composable("monthlyIncome") {
            MonthlyIncomePage(
                viewModel,
                navController
            )
        }
        composable(
            route = "addExpense?key={key}",
            arguments = listOf(navArgument("key") { nullable = true })
        ) { backStackEntry ->
            val expenseKey = backStackEntry.arguments?.getString("key")
            AddExpenseScreen(
                viewModel = viewModel,
                onCancel = { navController.popBackStack() },
                expenseKey = expenseKey
            )
        }
        composable("monthlyExpense") {
            MonthlyExpensePage(
                viewModel,
                navController
            )
        }
    }
}


class FinanceViewModel : ViewModel() {
    private val database = Firebase.database
    private val incomeRef = database.getReference("incomeEntries")
    private val expenseRef = database.getReference("expenseEntries")
    private val savingsRef = database.getReference("currentSavings") // Add a reference for savings
    private val goalsRef = database.getReference("financeGoals")
    private val savingsGoalRef = database.getReference("savingsGoal")

    var financeGoals by mutableStateOf<List<FinanceGoal>>(emptyList())
    var savingsGoal by mutableStateOf(0.0)
    var incomeEntries by mutableStateOf<List<IncomeEntry>>(emptyList())
    var expenseEntries by mutableStateOf<List<ExpenseEntry>>(emptyList())
    var currentSavings by mutableStateOf(0.0)

    init {
        loadIncomeEntries()
        loadExpenseEntries()
        loadCurrentSavings()
        loadFinanceGoals()
        loadSavingsGoal()
    }

    private fun loadFinanceGoals() {
        goalsRef.get().addOnSuccessListener { snapshot ->
            val goals = snapshot.children.mapNotNull {
                it.getValue(FinanceGoal::class.java)
            }
            financeGoals = goals
            updateSavingsGoal()
        }
    }

    private fun loadSavingsGoal() {
        savingsGoalRef.get().addOnSuccessListener { snapshot ->
            savingsGoal = snapshot.getValue(Double::class.java) ?: 0.0
        }
    }

    private fun loadIncomeEntries() {
        incomeRef.get().addOnSuccessListener { snapshot ->
            val entries = snapshot.children.mapNotNull {
                val entry = it.getValue(IncomeEntry::class.java)
                entry?.copy(key = it.key)
            }
            incomeEntries = entries.sortedBy { it.getLocalDate() }
            calculateAndSaveCurrentSavings()
        }
    }

    private fun loadExpenseEntries() {
        expenseRef.get().addOnSuccessListener { snapshot ->
            val entries = snapshot.children.mapNotNull {
                val entry = it.getValue(ExpenseEntry::class.java)
                entry?.copy(key = it.key)
            }
            expenseEntries = entries.sortedBy { it.getLocalDate() }
            calculateAndSaveCurrentSavings()
        }
    }

    private fun loadCurrentSavings() {
        savingsRef.get().addOnSuccessListener { snapshot ->
            val savings = snapshot.getValue(Double::class.java) ?: 0.0
            currentSavings = savings
        }
    }

    private fun calculateAndSaveCurrentSavings() {
        val currentMonthDate = LocalDate.now().withDayOfMonth(1)
        val totalIncome = incomeEntries
            .filter { it.getLocalDate().withDayOfMonth(1) == currentMonthDate }
            .sumOf { it.amount }
        val totalExpenses = expenseEntries
            .filter { it.getLocalDate().withDayOfMonth(1) == currentMonthDate }
            .sumOf { it.amount }

        currentSavings = totalIncome - totalExpenses

        // Save current savings to Firebase
        savingsRef.setValue(currentSavings)
    }

    fun addIncome(date: LocalDate, type: String, amount: Double, description: String) {
        val dateString = date.format(DateTimeFormatter.ISO_DATE)
        val newEntry = IncomeEntry(date = dateString, type = type, amount = amount, description = description)
        val key = incomeRef.push().key ?: return
        incomeRef.child(key).setValue(newEntry.copy(key = key))
        loadIncomeEntries()
    }

    fun editIncome(key: String, date: LocalDate, type: String, amount: Double, description: String) {
        val dateString = date.format(DateTimeFormatter.ISO_DATE)
        val updatedEntry = IncomeEntry(date = dateString, type = type, amount = amount, description = description, key = key)
        incomeRef.child(key).setValue(updatedEntry)
        loadIncomeEntries()
    }

    fun deleteIncome(key: String) {
        incomeRef.child(key).removeValue()
        loadIncomeEntries()
    }

    fun addExpense(date: LocalDate, type: String, amount: Double, description: String) {
        val dateString = date.format(DateTimeFormatter.ISO_DATE)
        val newEntry = ExpenseEntry(date = dateString, type = type, amount = amount, description = description)
        val key = expenseRef.push().key ?: return
        expenseRef.child(key).setValue(newEntry.copy(key = key))
        loadExpenseEntries()
    }

    fun editExpense(key: String, date: LocalDate, type: String, amount: Double, description: String) {
        val dateString = date.format(DateTimeFormatter.ISO_DATE)
        val updatedEntry = ExpenseEntry(date = dateString, type = type, amount = amount, description = description, key = key)
        expenseRef.child(key).setValue(updatedEntry)
        loadExpenseEntries()
    }

    fun deleteExpense(key: String) {
        expenseRef.child(key).removeValue()
        loadExpenseEntries()
    }

    private fun updateSavingsGoal() {
        savingsGoal = financeGoals.sumByDouble { it.monthlySavings }
        savingsGoalRef.setValue(savingsGoal)
    }

    fun addGoal(name: String, downPayment: Double, startDate: LocalDate, endDate: LocalDate, monthlySavings: Double) {
        val newGoal = FinanceGoal(name, downPayment, monthlySavings)

        val goalKey = goalsRef.push().key ?: return
        goalsRef.child(goalKey).setValue(newGoal)

        loadFinanceGoals()
    }
}

class RegistrationViewModel : ViewModel(){
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    fun signIn(email: String, password: String, onResult: (String) -> Unit) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onResult("Sign-in successful")
                } else {
                    onResult("Sign-in failed: ${task.exception?.message}")
                }
            }
    }

    fun register(email: String, password: String, onResult: (String) -> Unit){
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener{
                    task ->
                if(task.isSuccessful){
                    onResult("Registration successful")
                } else {
                    onResult("Registration failed: ${task.exception?.message}")
                }
            }
    }
}

@Composable
fun LandingPage(onRegisterClick: () -> Unit, onSignInClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.house),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Plan your finances and save towards your goal",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Register Button
        Button(
            onClick = onRegisterClick,
            modifier = Modifier.fillMaxWidth(0.8f),
            colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.primary)
        ) {
            Text("Register")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Sign In Button
        TextButton(
            onClick = onSignInClick,
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            Text("Sign in", color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
fun SignInPage(
    signInViewModel: RegistrationViewModel,
    onSignInSuccess: () -> Unit,
    onRegisterClick: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Message at the top
        if (message.isNotEmpty()) {
            Text(text = message, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(16.dp))
        }

        TextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            signInViewModel.signIn(email, password) { result ->
                if (result == "Sign-in successful") {
                    onSignInSuccess()
                } else {
                    message = result
                }
            }
        }) {
            Text("Sign In")
        }
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onRegisterClick) {
            Text("Register", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun RegistrationPage(viewModel: RegistrationViewModel, onCancelClick: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Message at the top
        if (message.isNotEmpty()) {
            Text(text = message, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(16.dp))
        }

        TextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("Confirm Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            if (password == confirmPassword) {
                viewModel.register(email, password) { result ->
                    message = result
                }
            } else {
                message = "Passwords do not match"
            }
        }) {
            Text("Register")
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = onCancelClick) {
            Text("Cancel", color = MaterialTheme.colorScheme.primary)
        }
    }
}


@Composable
fun HomeScreen(
    viewModel: FinanceViewModel,
    onAddNewGoalClick: () -> Unit,
    onViewIncomeClick: () -> Unit,
    onViewExpenseClick: () -> Unit,
    onSignOutClick: () -> Unit, // New parameter for sign out action
    onAddExpenseClick: () -> Unit,
    onAddIncomeClick: () -> Unit
) {
    val financeGoals = viewModel.financeGoals
    val savingsGoal = viewModel.savingsGoal
    val currentMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM"))
    val currentSavings = viewModel.currentSavings
    val auth = remember { FirebaseAuth.getInstance() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = {
                auth.signOut() // Sign out the user
                onSignOutClick()
            }) {
                Text("Sign Out")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                // Month at the top inside the card
                Text(
                    text = currentMonth,
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Savings Goal and Current Savings horizontally
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Savings Goal", style = MaterialTheme.typography.bodyLarge)
                        Text("${savingsGoal.toInt()} CAD", style = MaterialTheme.typography.headlineSmall)
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Current Savings", style = MaterialTheme.typography.bodyLarge)
                        Text("${currentSavings.toInt()} CAD", style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Finance Goals", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)

        Spacer(modifier = Modifier.height(8.dp))

        for (goal in financeGoals) {
            Text("${goal.name}", style = MaterialTheme.typography.bodyLarge)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onAddNewGoalClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.primary)
        ) {
            Text("Add New Goal")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onViewIncomeClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.primary)
        ) {
            Text("Income")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onAddIncomeClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.primary)
        ) {
            Text("Add Income")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onViewExpenseClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.primary)
        ) {
            Text("Expense")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onAddExpenseClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.primary)
        ) {
            Text("Add Expense")
        }
    }
}

@Composable
fun AddGoalScreen(viewModel: FinanceViewModel, onCancel: () -> Unit) {
    var goalName by remember { mutableStateOf(TextFieldValue("")) }
    var downPayment by remember { mutableStateOf(TextFieldValue("")) }
    var startDate by remember { mutableStateOf(LocalDate.now()) }
    var endDate by remember { mutableStateOf(LocalDate.now().plusMonths(1)) }
    var monthlySavings by remember { mutableStateOf(0.0) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        TextButton(onClick = { onCancel() }) {
            Text("Cancel")
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = goalName,
            onValueChange = { goalName = it },
            label = { Text("Enter Finance Goal") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = downPayment,
            onValueChange = { downPayment = it },
            label = { Text("Down Payment") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = { showStartDatePicker = true }) {
            Text("Savings Start Date: ${startDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))}")
        }

        if (showStartDatePicker) {
            DatePickerDialog(
                initialDate = startDate,
                onDateSelected = { date ->
                    startDate = date
                    showStartDatePicker = false
                },
                onDismissRequest = { showStartDatePicker = false }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = { showEndDatePicker = true }) {
            Text("Savings End Date: ${endDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))}")
        }

        if (showEndDatePicker) {
            DatePickerDialog(
                initialDate = endDate,
                onDateSelected = { date ->
                    endDate = date
                    showEndDatePicker = false
                },
                onDismissRequest = { showEndDatePicker = false }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                val downPaymentValue = downPayment.text.toDoubleOrNull() ?: 0.0
                monthlySavings = calculateMonthlySavings(downPaymentValue, startDate, endDate)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.primary)
        ) {
            Text("Calculate Monthly Savings")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text("Monthly Savings: ${monthlySavings.toInt()} CAD", style = MaterialTheme.typography.bodyLarge)

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val downPaymentValue = downPayment.text.toDoubleOrNull() ?: 0.0
                viewModel.addGoal(goalName.text, downPaymentValue, startDate, endDate, monthlySavings)
                onCancel()
            },
            colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.primary),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Add Goal")
        }
    }
}

fun calculateMonthlySavings(downPayment: Double, startDate: LocalDate, endDate: LocalDate): Double {
    val monthsUntilGoal = ChronoUnit.MONTHS.between(startDate, endDate).toInt()
    return if (monthsUntilGoal > 0) downPayment / monthsUntilGoal else 0.0
}


@Composable
fun AddExpenseScreen(viewModel: FinanceViewModel, onCancel: () -> Unit, expenseKey: String? = null) {
    var expenseDate by remember { mutableStateOf(LocalDate.now()) }
    var expenseType by remember { mutableStateOf(TextFieldValue("")) }
    var amount by remember { mutableStateOf(TextFieldValue("")) }
    var description by remember { mutableStateOf(TextFieldValue("")) }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(expenseKey) {
        if (expenseKey != null) {
            val entry = viewModel.expenseEntries.find { it.key == expenseKey }
            if (entry != null) {
                expenseDate = entry.getLocalDate()
                expenseType = TextFieldValue(entry.type)
                amount = TextFieldValue(entry.amount.toString())
                description = TextFieldValue(entry.description)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Button(onClick = { onCancel() }) {
            Text("Cancel")
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = expenseType,
            onValueChange = { expenseType = it },
            label = { Text("Expense Type") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Amount") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = { showDatePicker = true }) {
            Text("Expense Date: ${expenseDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))}")
        }

        if (showDatePicker) {
            DatePickerDialog(
                initialDate = expenseDate,
                onDateSelected = { date ->
                    expenseDate = date
                    showDatePicker = false
                },
                onDismissRequest = { showDatePicker = false }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val amountValue = amount.text.toDoubleOrNull() ?: 0.0
                if (expenseType.text.isNotEmpty() && amountValue > 0) {
                    if (expenseKey == null) {
                        viewModel.addExpense(expenseDate, expenseType.text, amountValue, description.text)
                    } else {
                        viewModel.editExpense(expenseKey, expenseDate, expenseType.text, amountValue, description.text)
                    }
                    onCancel()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.primary)
        ) {
            Text(if (expenseKey == null) "Submit" else "Update")
        }
    }
}


@Composable
fun AddIncomeScreen(viewModel: FinanceViewModel, onCancel: () -> Unit, incomeKey: String? = null) {
    var incomeDate by remember { mutableStateOf(LocalDate.now()) }
    var incomeType by remember { mutableStateOf(TextFieldValue("")) }
    var amount by remember { mutableStateOf(TextFieldValue("")) }
    var description by remember { mutableStateOf(TextFieldValue("")) }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(incomeKey) {
        if (incomeKey != null) {
            val entry = viewModel.incomeEntries.find { it.key == incomeKey }
            if (entry != null) {
                incomeDate = entry.getLocalDate()
                incomeType = TextFieldValue(entry.type)
                amount = TextFieldValue(entry.amount.toString())
                description = TextFieldValue(entry.description)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Button(onClick = { onCancel() }) {
            Text("Cancel")
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = incomeType,
            onValueChange = { incomeType = it },
            label = { Text("Income Type") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Amount") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = { showDatePicker = true }) {
            Text("Income Date: ${incomeDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))}")
        }

        if (showDatePicker) {
            DatePickerDialog(
                initialDate = incomeDate,
                onDateSelected = { date ->
                    incomeDate = date
                    showDatePicker = false
                },
                onDismissRequest = { showDatePicker = false }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val amountValue = amount.text.toDoubleOrNull() ?: 0.0
                if (incomeType.text.isNotEmpty() && amountValue > 0) {
                    if (incomeKey == null) {
                        viewModel.addIncome(incomeDate, incomeType.text, amountValue, description.text)
                    } else {
                        viewModel.editIncome(incomeKey, incomeDate, incomeType.text, amountValue, description.text)
                    }
                    onCancel()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.primary)
        ) {
            Text(if (incomeKey == null) "Submit" else "Update")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MonthlyIncomePage(viewModel: FinanceViewModel, navController: NavController) {
    val incomeEntries = viewModel.incomeEntries.groupBy { it.getLocalDate().withDayOfMonth(1) }
    val months = incomeEntries.keys.sorted()
    val pagerState = rememberPagerState(initialPage = 0)
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        TextButton(
            onClick = { navController.popBackStack() },
        ) {
            Text("Back")
        }

        Text(
            "Income",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (months.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            if (pagerState.currentPage > 0) {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        }
                    }
                ) {
                    Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Previous Month")
                }

                Text(
                    text = months[pagerState.currentPage].format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center
                )

                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            if (pagerState.currentPage < months.size - 1) {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    }
                ) {
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Next Month")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalPager(
                state = pagerState,
                count = months.size
            ) { pageIndex ->
                val month = months[pageIndex]
                val incomesGroupedByDate = incomeEntries[month]?.groupBy { it.getLocalDate() } ?: emptyMap()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        incomesGroupedByDate.keys.sorted().forEach { date ->
                            item {
                                Text(
                                    text = date.format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
                                    style = MaterialTheme.typography.bodyLarge
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                incomesGroupedByDate[date]?.forEach { income ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${income.type}: ${income.amount} CAD",
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = income.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Row {
                                            IconButton(onClick = {
                                                navController.navigate("addIncome?key=${income.key}")
                                            }) {
                                                Icon(Icons.Default.Edit, contentDescription = "Edit")
                                            }

                                            Spacer(modifier = Modifier.width(8.dp))

                                            IconButton(onClick = {
                                                income.key?.let { key -> viewModel.deleteIncome(key) }
                                            }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No income entries available.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { navController.navigate("addIncome") },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.primary)
        ) {
            Text("Add Income")
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MonthlyExpensePage(viewModel: FinanceViewModel, navController: NavController) {
    val expenseEntries = viewModel.expenseEntries.groupBy { it.getLocalDate().withDayOfMonth(1) }
    val months = expenseEntries.keys.sorted()
    val pagerState = rememberPagerState(initialPage = 0)
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        TextButton(
            onClick = { navController.popBackStack() },
        ) {
            Text("back")
        }
        Text(
            "Expenses",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (months.isEmpty()) {
            // Display a message if there are no expenses
            Text(
                text = "No expenses available",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            if (pagerState.currentPage > 0) {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        }
                    }
                ) {
                    Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Previous Month")
                }

                Text(
                    text = months[pagerState.currentPage].format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center
                )

                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            if (pagerState.currentPage < months.size - 1) {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    }
                ) {
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Next Month")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalPager(
                state = pagerState,
                count = months.size
            ) { pageIndex ->
                val month = months[pageIndex]
                val expensesGroupedByDate = expenseEntries[month]?.groupBy { it.getLocalDate() } ?: emptyMap()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        expensesGroupedByDate.keys.sorted().forEach { date ->
                            item {
                                Text(
                                    text = date.format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
                                    style = MaterialTheme.typography.bodyLarge
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                expensesGroupedByDate[date]?.forEach { expense ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${expense.type}: ${expense.amount} CAD",
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = expense.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Row {
                                            IconButton(onClick = {
                                                navController.navigate("addExpense?key=${expense.key}")
                                            }) {
                                                Icon(Icons.Default.Edit, contentDescription = "Edit")
                                            }

                                            Spacer(modifier = Modifier.width(8.dp))

                                            IconButton(onClick = {
                                                expense.key?.let { key -> viewModel.deleteExpense(key) }
                                            }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { navController.navigate("addExpense") },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.primary)
        ) {
            Text("Add Expense")
        }
    }
}



@Composable
fun DatePickerDialog(
    initialDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val datePicker = android.app.DatePickerDialog(
        context,
        { _, year, month, day ->
            val selectedDate = LocalDate.of(year, month + 1, day)
            onDateSelected(selectedDate)
        },
        initialDate.year,
        initialDate.monthValue - 1,
        initialDate.dayOfMonth
    )

    datePicker.setOnDismissListener { onDismissRequest() }
    LaunchedEffect(Unit) { datePicker.show() }
}


@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    FinanceTheme {
        FinanceApp()
    }
}