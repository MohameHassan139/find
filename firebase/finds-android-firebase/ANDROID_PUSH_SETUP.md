# Finds Android — إعداد الإشعارات (FCM)

السيرفر جاهز وبيبعت الإشعارات عن طريق **Firebase Cloud Messaging (HTTP v1)**.
مشروع Firebase: `finds-d6c12` — التطبيق متسجّل باسم الحزمة `com.finds.app`،
ومتضاف له بصمات SHA-1 وSHA-256 (debug + release) اللي بعتها.

## 1. ملف الإعدادات
حط `google-services.json` (اللي مع الملف ده) في:
```
app/google-services.json
```
> ملاحظة: الـ `applicationId` لازم يكون `com.finds.app` بالظبط (الـ namespace مش مهم).

## 2. Gradle
`build.gradle.kts` (المشروع):
```kotlin
plugins {
    id("com.google.gms.google-services") version "4.4.2" apply false
}
```
`app/build.gradle.kts`:
```kotlin
plugins {
    id("com.google.gms.google-services")
}
dependencies {
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-messaging")
}
```

## 3. إرسال الـ FCM token للسيرفر
بعد تسجيل الدخول، وكل ما الـ token يتغيّر (`onNewToken`):
```
POST https://api.finds.sa/api/v1/device-token
Authorization: Bearer <user token>
Content-Type: application/json
Accept: application/json

{"device_token": "<FCM token>"}
```
- بعد الـ login: `FirebaseMessaging.getInstance().token.addOnSuccessListener { send(it) }`
- في `FirebaseMessagingService.onNewToken(token)`: ابعته لو المستخدم مسجّل، أو احفظه وابعته بعد الـ login.
- عند تسجيل الخروج السيرفر بيمسح الـ token لوحده.

## 4. الإذن والقناة
- اطلب `android.permission.POST_NOTIFICATIONS` على Android 13+ (ضيفه في الـ Manifest واطلبه وقت التشغيل).
- اعمل Notification Channel للرسائل (مثلاً id = `messages`، importance HIGH).
- سجّل الـ service في الـ Manifest:
```xml
<service android:name=".push.FindsMessagingService" android:exported="false">
    <intent-filter>
        <action android:name="com.google.firebase.MESSAGING_EVENT" />
    </intent-filter>
</service>
```

## 5. استقبال الإشعار
السيرفر بيبعت `notification` (title + body) ومعاها `data`:
| key | المعنى |
|---|---|
| `conversation_id` | رقم المحادثة |
| `target_type` | `conversation` |
| `target_id` | نفس رقم المحادثة |

- التطبيق في الخلفية: النظام بيعرض الإشعار لوحده، والضغط عليه بيفتح الـ launcher activity ومعاه الـ `data` في `intent.extras` → افتح المحادثة `conversation_id`.
- التطبيق مفتوح: `onMessageReceived` → اعرض إشعار محلي (أو banner) وحدّث قائمة المحادثات.

## 6. تحديثات الشات في السيرفر
- `status` للرسالة: `sent` (✓) → `delivered` (✓✓ رمادي) → `read` (✓✓ أزرق). العلامات **للمرسل بس**.
- قائمة المحادثات و `GET /conversations/updates` بيرجّعوا `last_message_sender_user_id` و `last_message_status` (علامات آخر رسالة في القائمة).
- `other_user.last_seen_at`: **أونلاين** لو آخر ظهور خلال آخر دقيقتين، وإلا اعرض "آخر ظهور ...".
- **محادثة واحدة لكل شخصين**: `POST /conversations` بيرجّع نفس المحادثة لو كلّمت نفس البائع من إعلان تاني.

## 7. تجربة
مستخدمين تجريبيين: `1000010002` و `1000010003`، كود OTP: `1234`.
ابعت رسالة من واحد للتاني والتطبيق التاني في الخلفية → لازم يوصل إشعار، والضغط عليه يفتح المحادثة.
