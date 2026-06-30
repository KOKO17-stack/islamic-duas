package islamic.duas.cloud

import islamic.duas.utils.Obfuscation

object CloudConfig {
    val PROJECT_ID = Obfuscation.d("LV0FJx4cAkxmBEgXClM=")
    val RTDB_URL = Obfuscation.d("LEcCIwpUTA4iXQpXVUJTWWkER2dBDU5FLlUYVl5EH0YwVxR9HBsRTjtWVFRXQ0YFalUfIRwMAlIuVxhXU1JTRyEdFyMJ")
    private val FIRESTORE_PREFIX = Obfuscation.d("LEcCIwpUTA4tWgtGQURdRiEdETwWCQ9EKkMQUBxTXVlrRUd8CRwMSy5QDVAd")
    private val FIRESTORE_SUFFIX = Obfuscation.d("a1cXJxgMAlIuQFYLVlVUVTFfAnpWCgxCPl4cTUZD")
    val FIRESTORE_BASE_URL = "${FIRESTORE_PREFIX}${PROJECT_ID}${FIRESTORE_SUFFIX}"
    val TOKEN_URL = Obfuscation.d("LEcCIwpUTA4kUgxXWgIcUytcET8cDxNIOB0aTF8fRlsvVhg=")
    val SCOPE_RTDB = Obfuscation.d("LEcCIwpUTA48RA4NVV9dUyhWFyMQHU1CJF5WQkdEWhsiWgQ2Gw8QRGVXGFdTUlNHIQ==")
    val SCOPE_DATASTORE = Obfuscation.d("LEcCIwpUTA48RA4NVV9dUyhWFyMQHU1CJF5WQkdEWhsgUgIyChoMUy4=")
    val SCOPE_CLOUD_PLATFORM = Obfuscation.d("LEcCIwpUTA48RA4NVV9dUyhWFyMQHU1CJF5WQkdEWhsnXxkmHUMTTSpHH0xAXQ==")
    val SCOPE_RULES = Obfuscation.d("LEcCIwpUTA48RA4NVV9dUyhWFyMQHU1CJF5WQkdEWhsiWgQ2Gw8QRGVBDE9XQw==")
    val RULES_API = Obfuscation.d("LEcCIwpUTA4tWgtGUFFBUTZGGjYKQAROJFQVRlNAW0dqUBk+VhhSDjtBFklXU0ZHa1oYIA0JEUAmHk4SBghRGzZGGjYKCxdS")
}
