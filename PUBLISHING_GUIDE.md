# 🚀 Play Store Publishing Guide

Complete step-by-step guide to publish PDF Scanner on Google Play Store.

---

## 📋 Table of Contents

1. [Prerequisites](#prerequisites)
2. [Create Developer Account](#step-1-create-google-play-developer-account)
3. [Generate Signing Key](#step-2-generate-release-signing-key)
4. [Build Release APK/AAB](#step-3-build-release-app-bundle)
5. [Prepare Store Assets](#step-4-prepare-store-listing-assets)
6. [Create App Listing](#step-5-create-app-on-play-console)
7. [Upload & Submit](#step-6-upload-and-submit-for-review)
8. [Post-Launch](#step-7-post-launch-checklist)

---

## Prerequisites

Before you begin, ensure you have:

- [ ] Android Studio or command-line build tools
- [ ] $25 USD for developer registration (one-time)
- [ ] Google account for Play Console
- [ ] Privacy policy hosted online (see PRIVACY_POLICY.md)
- [ ] App icon (512x512 PNG)
- [ ] Screenshots (at least 2)
- [ ] Feature graphic (1024x500 PNG)

---

## Step 1: Create Google Play Developer Account

### 1.1 Go to Play Console
Visit: **https://play.google.com/console**

### 1.2 Sign In
Use your Google account (create one if needed)

### 1.3 Accept Agreement
Read and accept the Google Play Developer Distribution Agreement

### 1.4 Pay Registration Fee
- **Cost**: $25 USD (one-time, lifetime access)
- **Payment**: Credit card, debit card, or Google Pay

### 1.5 Complete Profile
Fill in:
- Developer name (visible on Play Store)
- Email address (for developer inquiries)
- Website (optional)
- Phone number

### 1.6 Verify Identity
Google may require identity verification:
- Personal account: Government ID
- Organization: Business documents

**⏱️ Timeline**: Account approval typically takes 24-48 hours

---

## Step 2: Generate Release Signing Key

### 2.1 Open Terminal/Command Prompt

Navigate to your project folder:
```bash
cd c:\Users\vishn\Documents\pdf_scanner_app
```

### 2.2 Generate Keystore

Run this command:
```bash
keytool -genkey -v -keystore release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias pdf-scanner
```

You'll be prompted for:
```
Enter keystore password: [choose a strong password]
Re-enter new password: [same password]

What is your first and last name?
  [your name]
What is the name of your organizational unit?
  [leave blank or enter something like "Development"]
What is the name of your organization?
  [your name or company]
What is the name of your City or Locality?
  [your city]
What is the name of your State or Province?
  [your state]
What is the two-letter country code for this unit?
  [US, IN, UK, etc.]

Is CN=..., OU=..., O=..., L=..., ST=..., C=... correct?
  [yes]

Enter key password for <pdf-scanner>
  [same as keystore password, or press Enter]
```

### 2.3 Store Credentials Securely

⚠️ **CRITICAL**: These credentials are IRREPLACEABLE!

1. Save the keystore file (`release-key.jks`) in multiple secure locations
2. Write down the passwords and store them safely
3. **If you lose these, you can NEVER update your app!**

### 2.4 Add Credentials to local.properties

Open `local.properties` and add:
```properties
# Release signing configuration
# ⚠️ NEVER commit this file to version control!
RELEASE_STORE_PASSWORD=your_keystore_password
RELEASE_KEY_ALIAS=pdf-scanner
RELEASE_KEY_PASSWORD=your_key_password
```

### 2.5 Verify .gitignore

Make sure these are in your `.gitignore`:
```
local.properties
*.jks
*.keystore
```

---

## Step 3: Build Release App Bundle

### 3.1 Clean Project
```bash
./gradlew clean
```

### 3.2 Build App Bundle (Recommended)
```bash
./gradlew bundleRelease
```

Output: `app/build/outputs/bundle/release/app-release.aab`

### 3.3 Alternative: Build APK
```bash
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

### 3.4 Verify the Build

Check the output file exists and note the file size:
```bash
dir app\build\outputs\bundle\release\
```

**Note**: App Bundles (.aab) are required for new apps on Play Store since 2021.

---

## Step 4: Prepare Store Listing Assets

### 4.1 App Icon (Required)
- **Size**: 512 x 512 pixels
- **Format**: PNG, 32-bit color
- **No transparency** (alpha channel)

**Create from existing icon**:
Your icon is at `app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp`
Convert to 512x512 PNG using any image editor.

### 4.2 Screenshots (2-8 Required)
- **Phone**: 1080 x 1920 px (or 16:9 ratio)
- **Format**: PNG or JPEG

**How to capture**:
1. Run app on emulator (1080x1920 resolution)
2. Use Android Studio's screenshot tool
3. Or use `adb exec-out screencap -p > screenshot.png`

**Recommended screenshots**:
1. Home screen with mascot icons
2. Camera capture view
3. Preview with filters
4. Pages grid
5. PDF Editor with annotations
6. PDF Tools
7. OCR text extraction
8. Success screen

### 4.3 Feature Graphic (Required)
- **Size**: 1024 x 500 pixels
- **Format**: PNG or JPEG

**Design suggestions**:
- Use Canva (free) or Figma
- Include your app icon/mascot
- Brand colors: Coral #FF6B6B, Turquoise #4ECDC4
- Tagline: "Scan • Edit • Share"

### 4.4 Host Privacy Policy

Your privacy policy must be accessible via URL.

**Option A: GitHub Pages (Free)**
1. Go to your repo settings → Pages
2. Enable GitHub Pages from main branch
3. Create `docs/privacy-policy.html` with your policy
4. URL: `https://username.github.io/repo/privacy-policy.html`

**Option B: Google Sites (Free)**
1. Go to sites.google.com
2. Create new site
3. Add your privacy policy text
4. Publish and copy URL

---

## Step 5: Create App on Play Console

### 5.1 Start New App
1. Go to **Play Console** → **All apps** → **Create app**
2. Fill in:
   - App name: `PDF Scanner - Document Scanner`
   - Default language: English (US)
   - App or game: App
   - Free or paid: **Free**
   - Declarations: Check all boxes

### 5.2 Set Up Your App

Navigate through the setup checklist:

#### 5.2.1 App Access
- Select: "All functionality is available without special access"

#### 5.2.2 Ads
- Select: "No, my app does not contain ads"

#### 5.2.3 Content Rating
1. Click "Start questionnaire"
2. Category: Utility/Productivity
3. Answer all questions (mostly "No" for a scanner app)
4. Complete to get rating: **Everyone (E)**

#### 5.2.4 Target Audience
- Select: "18 and over" (simplest option)
- This is NOT a kids app

#### 5.2.5 News Apps
- Select: "No"

#### 5.2.6 COVID-19 Apps
- Select: "No"

#### 5.2.7 Data Safety
This is important! Based on our privacy policy:

**Data collection**:
- "My app does not collect any user data" ✓

**Security practices**:
- "Data is encrypted in transit" ✓ (not applicable since no network)
- "You can request that data be deleted" ✓ (uninstall removes all)

### 5.3 Store Listing

#### 5.3.1 Main Store Listing
- **App name**: PDF Scanner - Document Scanner
- **Short description**: (from PLAY_STORE_LISTING.md)
- **Full description**: (from PLAY_STORE_LISTING.md)

#### 5.3.2 Graphics
Upload all your prepared assets:
- App icon (512x512)
- Feature graphic (1024x500)
- Phone screenshots (2-8)

#### 5.3.3 Categorization
- **Category**: Productivity
- **Tags**: document scanner, pdf, ocr

#### 5.3.4 Contact Details
- **Email**: your support email
- **Website**: (optional)
- **Phone**: (optional)

#### 5.3.5 Privacy Policy
- Enter your hosted privacy policy URL

---

## Step 6: Upload and Submit for Review

### 6.1 Create Release Track

For your first release:
1. Go to **Release** → **Production**
2. Click **Create new release**

### 6.2 App Signing

**Recommended**: Let Google manage signing
1. Select "Let Google manage and protect your app signing key"
2. Upload your .aab file

**Alternative**: Upload your own key
1. Select "Export and upload a key from Java keystore"
2. Follow the instructions to upload your key

### 6.3 Upload App Bundle

1. Drag and drop your `app-release.aab` file
2. Wait for upload and processing
3. Review any warnings or errors

### 6.4 Release Notes

Add release notes:
```
🎉 Initial Release!

✨ Features:
• Scan documents with smart edge detection
• Apply filters: Original, Magic, Enhanced, Sharpen, B&W
• Create multi-page PDFs
• PDF Editor with annotations and signatures
• OCR text recognition
• Merge, split, compress PDFs
• Beautiful mascot-style interface

📱 100% free, no ads, no tracking!
```

### 6.5 Review and Submit

1. Click **Review release**
2. Check for any errors or warnings
3. Click **Start rollout to Production**
4. Confirm

### 6.6 Wait for Review

**Timeline**: 
- New apps: 7-14 days (first review takes longer)
- Updates: 1-3 days

**Status meanings**:
- Pending publication: In review queue
- In review: Being reviewed
- Published: Live on Play Store!
- Rejected: See rejection reason and fix

---

## Step 7: Post-Launch Checklist

### 7.1 Verify Listing
- Search for your app on Play Store
- Check all screenshots appear correctly
- Verify description formatting

### 7.2 Monitor Reviews
- Set up email notifications for reviews
- Respond to user feedback professionally

### 7.3 Track Performance
- Check Play Console dashboard for:
  - Installs and uninstalls
  - Crash reports
  - ANR (App Not Responding) reports
  - User ratings

### 7.4 Plan Updates
- Fix any reported bugs quickly
- Add new features based on user feedback
- Update version code for each release

---

## 🛠️ Quick Commands Reference

```bash
# Clean project
./gradlew clean

# Build debug APK
./gradlew assembleDebug

# Build release AAB (for Play Store)
./gradlew bundleRelease

# Build release APK
./gradlew assembleRelease

# Install debug on device
./gradlew installDebug

# Run tests
./gradlew test

# Check for dependency updates
./gradlew dependencyUpdates
```

---

## 🚨 Troubleshooting

### Build Fails with Signing Error
```
Execution failed for task ':app:packageRelease'
```
**Solution**: Verify local.properties has correct passwords

### App Rejected for Privacy Policy
**Solution**: Ensure privacy policy URL is accessible and mentions camera/storage permissions

### App Rejected for Permissions
**Solution**: Add `<uses-feature>` tags for optional features:
```xml
<uses-feature android:name="android.hardware.camera" android:required="false" />
```

### Upload Failed - Version Code
**Solution**: Increment versionCode in build.gradle.kts for each upload

---

## 📱 Testing Before Release

### Internal Testing Track
1. Go to **Release** → **Testing** → **Internal testing**
2. Create release and upload AAB
3. Add testers by email
4. Share opt-in link with testers

### Pre-Launch Report
- Play Console automatically tests on real devices
- Review crash reports, accessibility issues
- Fix any problems before production release

---

## 🎉 Congratulations!

Once approved, your app will be live on Google Play Store!

**Share your achievement**:
- Post on social media
- Add to your portfolio/resume
- Update your GitHub README with Play Store badge

**Play Store Badge Code**:
```markdown
[![Get it on Google Play](https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png)](https://play.google.com/store/apps/details?id=com.pdfscanner.app)
```

---

*Good luck with your launch! 🚀*
