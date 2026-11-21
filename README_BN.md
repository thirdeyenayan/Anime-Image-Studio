# Anime Image Studio — Windows

এটি SDXL + IP-Adapter-ভিত্তিক image-to-image Anime artwork তৈরির অ্যাপ।

## ব্যবহার
1. Windows Installer চালিয়ে ইনস্টল করুন।
2. Desktop থেকে Anime Image Studio খুলুন।
3. Original image আপলোড করুন।
4. চাইলে custom Style Reference image দিন। না দিলে built-in Anime style বেছে নিন।
5. Transformation Strength ও Reference Influence ঠিক করে Create চাপুন।
6. PNG output ফোল্ডারে সেভ হবে।

## GPU
আধুনিক NVIDIA CUDA GPU দরকার। 8GB VRAM ন্যূনতম ব্যবহারিক লক্ষ্য, 12GB+ ভালো। GeForce 820M 2GB-তে SDXL চলবে না, তবে CPU Cartoon fallback ব্যবহার করা যাবে। সমর্থিত NVIDIA GPU না থাকলে OpenCV-ভিত্তিক CPU Cartoon filter চলবে। এটি দ্রুত সাধারণ cartoon effect; SDXL-এর মতো বিস্তারিত AI anime generation নয়।

প্রথমবার কয়েক GB model files ডাউনলোড হবে। Model weights installer-এ নেই।

## সীমাবদ্ধতা
Reference style ও subject ধরে রাখার চেষ্টা করা হয়, কিন্তু একই মুখ/পোজ/স্টাইল হুবহু থাকবে এমন নিশ্চয়তা নেই। বাস্তব GPU-তে পরীক্ষা না হওয়া পর্যন্ত এই build-কে পরীক্ষিত final বলা যাবে না।

## Android / Mobile Web App (প্রাথমিক সংস্করণ)

রেসপনসিভ ওয়েব অ্যাপটি `web/` ফোল্ডারে আছে। NVIDIA CUDA-সমর্থিত GPU-সহ কম্পিউটারে চালান:

```bash
python -m pip install -r requirements.txt
python web_server.py
```

কম্পিউটারে `http://127.0.0.1:7861` খুলুন। একই Wi-Fi-তে থাকা Android ফোন থেকে `http://<কম্পিউটারের-LAN-IP>:7861` খুলে পরীক্ষা করা যাবে। Android-এ PWA হিসেবে ইনস্টল করতে HTTPS-সহ হোস্টিং প্রয়োজন (localhost ছাড়া service worker-এর জন্য secure context লাগে)।

ছবি ব্রাউজার থেকে সার্ভারে যাবে এবং AI মডেল সার্ভারের GPU-তে চলবে। কোনো paid inference API ব্যবহার করা হয়নি। প্রথমবার SDXL/IP-Adapter মডেল ডাউনলোড হবে।

এটি প্রাথমিক mobile web সংস্করণ। Login, subscription payment, user quota, production hosting এবং native Android APK এখনো তৈরি হয়নি।

## Windows Desktop App
Windows installer ইনস্টল করে চালু করলে একই responsive interface আপনার default browser-এ local PC-তে খুলবে। Desktop build-এ web UI এবং local FastAPI server থাকবে। AI model চালাতে 8GB+ VRAM-সহ উপযুক্ত NVIDIA CUDA GPU প্রয়োজন (12GB+ ভালো)। Installer-এর মধ্যে model weights নেই; প্রথমবার conversion-এর সময় ডাউনলোড হবে।

## Android APK — ফোনেই AI Anime তৈরি

Android-এর জন্য এখন Local Dream-এর arm64 AI ইঞ্জিনসহ APK তৈরি হয়; ফোনে অ্যাপের নাম দেখাবে **Anime Image Studio**। ছবি কোনো paid fal.ai/Render সার্ভারে পাঠানো হয় না। GitHub-এর **Actions → Build Anime Image Studio Android APK → Run workflow** চালিয়ে সফল হলে `Anime-Image-Studio-Android-APK` artifact থেকে APK নিন। বিস্তারিত: [android/README.md](android/README.md)। প্রথমবার SD 1.5 Anime মডেল ডাউনলোড করতে ইন্টারনেট লাগবে; মডেল ডাউনলোড হওয়ার পর ফোনে অফলাইনে জেনারেশন করা যাবে। 8GB RAM ফোনে 512×512 দিয়ে শুরু করুন; গতি ও স্থিতিশীলতা ফোনের chipset-এর উপর নির্ভর করবে।
