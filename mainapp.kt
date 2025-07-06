package com.example.weatherapp

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView // Import ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide // Import Glide
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

// --- Data Class for UI ---
data class WeatherData(
    val day: String,
    val condition: String, // Main text condition, e.g., "Clouds"
    val highTemp: String,
    val lowTemp: String,
    val description: String,
    val icon: String? = null // OpenWeatherMap icon code (e.g., "01d")
)

// --- OpenWeatherMap API Specific Data Classes (remain the same as before) ---
data class OpenWeatherMapResponse(
    @SerializedName("coord") val coordinates: Coordinates?,
    @SerializedName("weather") val weather: List<WeatherCondition>?,
    @SerializedName("base") val base: String?,
    @SerializedName("main") val main: MainWeather?,
    @SerializedName("visibility") val visibility: Int?,
    @SerializedName("wind") val wind: Wind?,
    @SerializedName("clouds") val clouds: Clouds?,
    @SerializedName("rain") val rain: Precipitation?,
    @SerializedName("snow") val snow: Precipitation?,
    @SerializedName("dt") val dateTime: Long?,
    @SerializedName("sys") val system: SystemInfo?,
    @SerializedName("timezone") val timezone: Int?,
    @SerializedName("id") val cityId: Int?,
    @SerializedName("name") val cityName: String?,
    @SerializedName("cod") val responseCode: Int?
)
data class Coordinates(@SerializedName("lon") val longitude: Double?, @SerializedName("lat") val latitude: Double?)
data class WeatherCondition(
    @SerializedName("id") val id: Int?,
    @SerializedName("main") val mainCondition: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("icon") val icon: String?
)
data class MainWeather(
    @SerializedName("temp") val temperature: Double?,
    @SerializedName("feels_like") val feelsLike: Double?,
    @SerializedName("temp_min") val tempMin: Double?,
    @SerializedName("temp_max") val tempMax: Double?,
    @SerializedName("pressure") val pressure: Int?,
    @SerializedName("humidity") val humidity: Int?,
    @SerializedName("sea_level") val seaLevelPressure: Int?,
    @SerializedName("grnd_level") val groundLevelPressure: Int?
)
data class Wind(@SerializedName("speed") val speed: Double?, @SerializedName("deg") val directionDegrees: Int?, @SerializedName("gust") val gust: Double?)
data class Clouds(@SerializedName("all") val all: Int?)
data class Precipitation(@SerializedName("1h") val last1h: Double?, @SerializedName("3h") val last3h: Double?)
data class SystemInfo(
    @SerializedName("type") val type: Int?,
    @SerializedName("id") val id: Int?,
    @SerializedName("country") val countryCode: String?,
    @SerializedName("sunrise") val sunriseTime: Long?,
    @SerializedName("sunset") val sunsetTime: Long?
)

// --- Retrofit API Service (remains the same) ---
interface OpenWeatherMapApiService {
    @GET("data/2.5/weather")
    suspend fun getCurrentWeather(
        @Query("q") cityNameCountry: String,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric"
    ): Response<OpenWeatherMapResponse>
}

// --- Retrofit Instance (remains the same) ---
object RetrofitInstance {
    private const val BASE_URL = "https://api.openweathermap.org/"
    private val loggingInterceptor = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
    private val httpClient = OkHttpClient.Builder().addInterceptor(loggingInterceptor).build()
    val api: OpenWeatherMapApiService by lazy {
        Retrofit.Builder().baseUrl(BASE_URL).client(httpClient)
            .addConverterFactory(GsonConverterFactory.create()).build()
            .create(OpenWeatherMapApiService::class.java)
    }
}

// --- ViewModel ---
class WeatherViewModel : ViewModel() {
    private val _weatherDataList = MutableLiveData<List<WeatherData>>()
    val weatherDataList: LiveData<List<WeatherData>> = _weatherDataList
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private const val OPENWEATHERMAP_API_KEY = "dd1c1b33156403c06902b3c0a1cb34fa" // Your API Key
    private const val DEFAULT_CITY = "Bengaluru,IN"

    fun fetchCurrentWeather(city: String = DEFAULT_CITY) {
        _isLoading.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                Log.d("WeatherViewModel", "Fetching OpenWeatherMap data for city: $city")
                val response = RetrofitInstance.api.getCurrentWeather(
                    cityNameCountry = city, apiKey = OPENWEATHERMAP_API_KEY, units = "metric"
                )
                Log.d("WeatherViewModel", "Response Code: ${response.code()}")

                if (response.isSuccessful) {
                    val owmData = response.body()
                    Log.d("WeatherViewModel", "Raw OpenWeatherMap Data: $owmData")

                    if (owmData?.weather != null && owmData.main != null && owmData.cityName != null) {
                        val weatherCondition = owmData.weather.firstOrNull()
                        val observationTime = formatEpochTime(owmData.dateTime, owmData.timezone)
                        val mainWeather = owmData.main
                        val weatherDescription = weatherCondition?.description?.replaceFirstChar {
                            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                        } ?: "N/A"
                        val iconCode = weatherCondition?.icon // Get the icon code

                        val uiData = WeatherData(
                            day = "${owmData.cityName} ($observationTime)",
                            condition = weatherCondition?.mainCondition ?: "N/A", // Text condition
                            highTemp = "${mainWeather.temperature?.roundToInt()}°C",
                            lowTemp = "${mainWeather.tempMin?.roundToInt()}°C / ${mainWeather.tempMax?.roundToInt()}°C",
                            description = "$weatherDescription. Feels like ${mainWeather.feelsLike?.roundToInt()}°C. " +
                                    "Humidity: ${mainWeather.humidity}%." +
                                    (owmData.wind?.speed?.let { " Wind: ${it.roundToInt()} m/s ${owmData.wind.directionDegrees?.let { deg -> getWindDirection(deg) } ?: ""}" } ?: ""),
                            icon = iconCode // Store icon code in WeatherData
                        )
                        _weatherDataList.postValue(listOf(uiData))
                        Log.d("WeatherViewModel", "Transformed UI Data: $uiData")
                    } else {
                        _error.postValue("Incomplete weather data received from OpenWeatherMap.")
                        Log.w("WeatherViewModel", "OpenWeatherMap data incomplete. Full response: $owmData")
                    }
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    _error.postValue("API Error (OpenWeatherMap): ${response.code()} - ${response.message()}. Details: $errorBody")
                    Log.e("WeatherViewModel", "API Error: ${response.code()} - ${response.message()}. Body: $errorBody")
                }
            } catch (e: Exception) {
                _error.postValue("Network or Parsing Error (OpenWeatherMap): ${e.message}")
                Log.e("WeatherViewModel", "Exception: ${e.localizedMessage}", e)
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    private fun formatEpochTime(epochSeconds: Long?, timezoneShiftSeconds: Int?): String {
        if (epochSeconds == null) return "Unknown time"
        return try {
            val sdf = SimpleDateFormat("h:mm a, MMM d", Locale.getDefault())
            val netDate = Date(epochSeconds * 1000)
            if (timezoneShiftSeconds != null) {
                sdf.timeZone = TimeZone.getTimeZone("GMT").apply { rawOffset = timezoneShiftSeconds * 1000 }
            } else {
                sdf.timeZone = TimeZone.getDefault()
            }
            sdf.format(netDate)
        } catch (e: Exception) { "Invalid time" }
    }

    private fun getWindDirection(degrees: Int): String {
        val directions = arrayOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
        return directions[(degrees / 22.5).roundToInt() % 16]
    }
}

// --- MainActivity (remains the same) ---
class MainActivity : AppCompatActivity() {
    private lateinit var weatherRecyclerView: RecyclerView
    private lateinit var weatherAdapter: WeatherAdapter
    private var weatherDataListInternal: MutableList<WeatherData> = mutableListOf()
    private lateinit var progressBar: ProgressBar
    private val weatherViewModel: WeatherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        weatherRecyclerView = findViewById(R.id.weather_recycler_view)
        progressBar = findViewById(R.id.progressBar)
        weatherRecyclerView.layoutManager = LinearLayoutManager(this)
        weatherAdapter = WeatherAdapter(weatherDataListInternal) // Pass the list
        weatherRecyclerView.adapter = weatherAdapter

        observeViewModel()
        weatherViewModel.fetchCurrentWeather()
    }

    private fun observeViewModel() {
        weatherViewModel.weatherDataList.observe(this) { weatherList ->
            weatherDataListInternal.clear()
            weatherDataListInternal.addAll(weatherList)
            weatherAdapter.notifyDataSetChanged()
        }
        weatherViewModel.isLoading.observe(this) { isLoading ->
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            weatherRecyclerView.visibility = if (isLoading) View.GONE else View.VISIBLE
        }
        weatherViewModel.error.observe(this) { errorMessage ->
            errorMessage?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
        }
    }
}

// --- RecyclerView Adapter ---
class WeatherAdapter(private val weatherList: List<WeatherData>) : // Constructor takes the list
    RecyclerView.Adapter<WeatherAdapter.WeatherViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WeatherViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.weather_item, parent, false)
        return WeatherViewHolder(view)
    }

    override fun onBindViewHolder(holder: WeatherViewHolder, position: Int) {
        val weatherItem = weatherList[position]
        holder.bind(weatherItem)
    }

    override fun getItemCount(): Int = weatherList.size

    class WeatherViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dayText: TextView = itemView.findViewById(R.id.day_text)
        private val conditionText: TextView = itemView.findViewById(R.id.condition_text)
        private val tempText: TextView = itemView.findViewById(R.id.temp_text)
        private val descriptionText: TextView = itemView.findViewById(R.id.description_text)
        private val weatherIconImageView: ImageView = itemView.findViewById(R.id.weather_icon_imageview) // ImageView

        fun bind(weather: WeatherData) {
            dayText.text = weather.day
            conditionText.text = weather.condition // Set text condition (e.g., "Clouds")
            tempText.text = weather.highTemp
            descriptionText.text = weather.description

            // Load weather icon using Glide
            if (!weather.icon.isNullOrEmpty()) {
                val iconUrl = "${WeatherStyleHelper.OWM_ICON_BASE_URL}${weather.icon}@2x.png"
                Glide.with(itemView.context)
                    .load(iconUrl)
                    // .placeholder(R.drawable.ic_weather_placeholder) // Optional: create a placeholder drawable
                    // .error(R.drawable.ic_weather_error) // Optional: create an error drawable
                    .into(weatherIconImageView)
            } else {
                // Optionally set a default image or hide the ImageView if no icon code
                weatherIconImageView.setImageResource(R.drawable.ic_weather_placeholder) // Example placeholder
            }

            // Apply text color for temperature
            WeatherStyleHelper.applyTemperatureColor(tempText, weather.highTemp)

            // The emoji-based icon setting in conditionText can be removed if the ImageView is primary,
            // or kept as a fallback/supplement. For now, WeatherStyleHelper.applyWeatherIcon is removed
            // from here since the ImageView handles the visual icon. The `conditionText` will show "Clouds", "Rain", etc.
        }
    }
}

// --- Weather Styling Helper ---
object WeatherStyleHelper {
    // Base URL for OpenWeatherMap icons (used by Adapter now)
    const val OWM_ICON_BASE_URL = "https://openweathermap.org/img/wn/" // Make it accessible

    // This function can still be used if you want text-based emojis somewhere else,
    // but the adapter is now using Glide for ImageView.
    fun getEmojiForCondition(conditionMain: String): String {
        return when (conditionMain.lowercase(Locale.getDefault())) {
            "thunderstorm" -> "⛈️"
            "drizzle", "rain" -> "🌧️"
            "snow" -> "❄️"
            "mist", "smoke", "haze", "dust", "fog", "sand", "ash", "squall", "tornado" -> "🌫️"
            "clear" -> "☀️"
            "clouds" -> "☁️"
            else -> "🌤️"
        }
    }

    fun applyTemperatureColor(tempText: TextView, tempString: String) {
        try {
            val tempValue = tempString.filter { it.isDigit() || it == '-' || it == '.' }
            if (tempValue.isNotEmpty()) {
                val temp: Int = tempValue.toDouble().roundToInt()
                when {
                    temp >= 30 -> tempText.setTextColor(Color.parseColor("#D32F2F"))
                    temp >= 25 -> tempText.setTextColor(Color.parseColor("#FF6B6B"))
                    temp >= 15 -> tempText.setTextColor(Color.parseColor("#4ECDC4"))
                    temp <= 5  -> tempText.setTextColor(Color.parseColor("#1976D2"))
                    else -> tempText.setTextColor(Color.parseColor("#45B7D1"))
                }
            } else { tempText.setTextColor(Color.BLACK) }
        } catch (e: NumberFormatException) {
            Log.e("WeatherStyleHelper", "Temp parsing error: '$tempString', ${e.message}")
            tempText.setTextColor(Color.BLACK)
        }
    }
}
