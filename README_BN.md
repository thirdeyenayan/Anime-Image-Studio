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
আধুনিক NVIDIA CUDA GPU দরকার। 8GB VRAM ন্যূনতম ব্যবহারিক লক্ষ্য, 12GB+ ভালো। GeForce 820M 2GB এই মডেল চালাতে পারবে না। CPU mode বন্ধ রাখা হয়েছে কারণ SDXL CPU-তে অত্যন্ত ধীর।

প্রথমবার কয়েক GB model files ডাউনলোড হবে। Model weights installer-এ নেই।

## সীমাবদ্ধতা
Reference style ও subject ধরে রাখার চেষ্টা করা হয়, কিন্তু একই মুখ/পোজ/স্টাইল হুবহু থাকবে এমন নিশ্চয়তা নেই। বাস্তব GPU-তে পরীক্ষা না হওয়া পর্যন্ত এই build-কে পরীক্ষিত final বলা যাবে না।
