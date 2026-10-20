/* Urdu for the app's own words. The app is written in English; when this device is set to Urdu, every piece of
   text that appears on screen is looked up here (a word-for-word list, plus patterns for lines with numbers
   or names in them) and swapped, and the page flips to right-to-left. Anything not listed simply stays in
   English, and names, amounts and dates' digits are never touched. Switching language reloads the page. */
(function () {
  const KEY = 'rf.lang';
  let lang = 'en';
  try { lang = JSON.parse(localStorage.getItem(KEY)) === 'ur' ? 'ur' : 'en'; } catch { /* default */ }

  const WEEKDAYS = { Monday: 'پیر', Tuesday: 'منگل', Wednesday: 'بدھ', Thursday: 'جمعرات', Friday: 'جمعہ', Saturday: 'ہفتہ', Sunday: 'اتوار',
    Mon: 'پیر', Tue: 'منگل', Wed: 'بدھ', Thu: 'جمعرات', Fri: 'جمعہ', Sat: 'ہفتہ', Sun: 'اتوار' };
  const MONTHS = { January: 'جنوری', February: 'فروری', March: 'مارچ', April: 'اپریل', May: 'مئی', June: 'جون', July: 'جولائی',
    August: 'اگست', September: 'ستمبر', October: 'اکتوبر', November: 'نومبر', December: 'دسمبر',
    Jan: 'جنوری', Feb: 'فروری', Mar: 'مارچ', Apr: 'اپریل', Jun: 'جون', Jul: 'جولائی', Aug: 'اگست', Sep: 'ستمبر', Sept: 'ستمبر',
    Oct: 'اکتوبر', Nov: 'نومبر', Dec: 'دسمبر' };

  const DICT = {
    // navigation and chrome
    'Summary': 'خلاصہ', 'Home': 'ہوم', 'Money': 'رقم', 'Team': 'ٹیم', 'For you': 'آپ کے لیے', 'Open Money': 'رقم کھولیں', 'Choose name': 'نام چنیں', 'No upcoming match yet': 'ابھی کوئی آنے والا میچ نہیں', 'Polls': 'پولز', 'Home cards': 'ہوم کارڈ', 'Days': 'دن', 'Stats': 'اعداد و شمار', 'Players': 'کھلاڑی', 'Announce': 'اعلانات', 'Settings': 'ترتیبات',
    'Admin': 'ایڈمن', 'Switch user': 'صارف تبدیل کریں', 'Notifications': 'اطلاعات', 'Close': 'بند کریں', 'Back': 'واپس',
    'Cricket Club Ledger': 'کرکٹ کلب لیجر', 'Red Falcons': 'ریڈ فالکنز', 'Cricket Club': 'کرکٹ کلب', 'Cricket Club · Team Ledger': 'کرکٹ کلب · ٹیم لیجر',
    'Loading the ledger…': 'لیجر لوڈ ہو رہا ہے…', 'Enter': 'داخل ہوں', 'Enter your name': 'اپنا نام لکھیں', 'Verify': 'تصدیق کریں',
    'Which player are you?': 'آپ کون سے کھلاڑی ہیں؟', 'Choose your name': 'اپنا نام چنیں', 'Choose your name…': 'اپنا نام چنیں…',
    'Your name': 'آپ کا نام', 'Your phrase': 'آپ کا فقرہ', 'or': 'یا', 'Falcon Night': 'فالکن نائٹ',
    'View only · this page refreshes by itself': 'صرف دیکھنے کے لیے · یہ صفحہ خود تازہ ہوتا ہے',
    'Trust this browser — don’t ask again here': 'اس براؤزر پر بھروسہ کریں — دوبارہ نہ پوچھیں',
    'Lost your phone? Type one of your recovery codes instead.': 'فون کھو گیا؟ اس کی جگہ اپنا کوئی ریکوری کوڈ لکھیں۔',
    'Code from your authenticator app': 'آپ کی تصدیقی ایپ کا کوڈ',
    'Pick your name on Summary to add photos.': 'تصاویر شامل کرنے کے لیے خلاصہ میں اپنا نام چنیں۔',
    'Pick your name on the Summary page first, so the app knows which player you are.': 'پہلے خلاصہ صفحے پر اپنا نام چنیں تاکہ ایپ کو معلوم ہو کہ آپ کون سے کھلاڑی ہیں۔',

    // profile request
    'Update your profile': 'اپنی پروفائل اپ ڈیٹ کریں', 'Update my profile': 'میری پروفائل اپ ڈیٹ کریں', 'Update now': 'ابھی اپ ڈیٹ کریں',
    'Update profile': 'پروفائل اپ ڈیٹ', 'Your profile': 'آپ کی پروفائل', 'Save profile': 'پروفائل محفوظ کریں', 'Jersey name': 'جرسی پر نام',
    'Name on your jersey': 'آپ کی جرسی پر نام', 'Player updates': 'کھلاڑیوں کی اپ ڈیٹس', 'Profile updated': 'پروفائل اپ ڈیٹ ہو گئی',
    'Plain message': 'سادہ پیغام', 'Profile request': 'پروفائل کی درخواست',
    'Thanks! Your profile is saved and the admin has been told.': 'شکریہ! آپ کی پروفائل محفوظ ہو گئی اور ایڈمن کو بتا دیا گیا ہے۔',
    'Your teammates see this on your player card. Press Save when you’re done and the admin is told.': 'آپ کے ساتھی اسے آپ کے پلیئر کارڈ پر دیکھتے ہیں۔ مکمل ہونے پر محفوظ کریں دبائیں اور ایڈمن کو بتا دیا جائے گا۔',
    'The admin asked everyone to add a photo, jersey name and number, and batting and bowling details.': 'ایڈمن نے سب سے تصویر، جرسی کا نام اور نمبر، اور بیٹنگ و باؤلنگ کی تفصیل شامل کرنے کو کہا ہے۔',
    'Tap the camera to add a photo.': 'تصویر لگانے کے لیے کیمرے پر ٹیپ کریں۔', 'Tap the photo to change it.': 'تصویر بدلنے کے لیے اس پر ٹیپ کریں۔',

    'Which player are you?': 'آپ کون سے کھلاڑی ہیں؟', 'Full name': 'پورا نام', 'Choose your name…': 'اپنا نام چنیں…',
    'Everyone in the squad': 'اسکواڈ میں سب', 'The squad': 'اسکواڈ',
    'Pick your name from the squad and you can edit your details.': 'اسکواڈ سے اپنا نام چنیں تاکہ آپ اپنی تفصیل بدل سکیں۔',

    'Profiles': 'پروفائلز', 'Not updated yet': 'ابھی اپ ڈیٹ نہیں ہوئی', 'Updated': 'اپ ڈیٹ شدہ', 'Not updated': 'اپ ڈیٹ نہیں', 'Update profile': 'پروفائل اپ ڈیٹ کریں',
    'Ask everyone to update': 'سب سے اپ ڈیٹ کرنے کو کہیں', 'Predictions': 'پیشگوئیاں', 'No prediction yet': 'ابھی پیشگوئی نہیں', 'Nobody': 'کوئی نہیں',
    'Red Falcons win': 'ریڈ فالکنز جیتیں گے', '✓ Right': '✓ درست', '✗ Wrong': '✗ غلط',
    'Tap a name to see it.': 'دیکھنے کے لیے نام پر ٹیپ کریں۔',

    'Send squad list to management': 'انتظامیہ کو اسکواڈ کی فہرست بھیجیں', 'Squad list for management': 'انتظامیہ کے لیے اسکواڈ کی فہرست',
    'Crop your photo': 'اپنی تصویر کاٹیں', 'Use photo': 'تصویر استعمال کریں', 'Zoom': 'زوم',
    'Drag to move the picture, and zoom so your face fills the circle.': 'تصویر کو کھسکائیں اور زوم کریں تاکہ آپ کا چہرہ دائرے میں بھر جائے۔',

    'Choose your name in Settings': 'ترتیبات میں اپنا نام چنیں', 'You are': 'آپ ہیں', 'Lock in your prediction?': 'اپنی پیشگوئی پکی کریں؟', 'Lock it in': 'پکی کریں',
    'See who predicted': 'دیکھیں کس نے کیا پیشگوئی کی', 'Prediction locked in.': 'پیشگوئی پکی ہو گئی۔',
    'Locked in. A prediction can’t be changed.': 'پکی ہو گئی۔ پیشگوئی بدلی نہیں جا سکتی۔',
    'Choose your squad name to vote, predict, answer polls and add photos.': 'ووٹ دینے، پیشگوئی کرنے، پول کا جواب دینے اور تصاویر لگانے کے لیے اپنا اسکواڈ کا نام چنیں۔',

    '(not finalized)': '(حتمی نہیں)',

    'Reporting time': 'رپورٹ کرنے کا وقت',

    'News': 'خبریں', 'Matches': 'میچ',

    'Fixtures': 'میچوں کا شیڈول', 'Our fixtures': 'ہمارے میچ', 'Full scorecard': 'مکمل اسکور کارڈ', 'Match details': 'میچ کی تفصیل', 'Live & upcoming': 'جاری اور آنے والے', 'Results': 'نتائج', 'Table': 'ٹیبل', 'Leaders': 'نمایاں کھلاڑی',

    'Kit sizes': 'کٹ کے سائز', 'Shirt size': 'شرٹ کا سائز', 'Shirt sizes': 'شرٹ کے سائز', 'Choose your shirt size': 'اپنی شرٹ کا سائز چنیں', 'Choose my shirt size': 'میری شرٹ کا سائز چنیں', 'Choose now': 'ابھی چنیں',
    'Shirt size and jersey': 'شرٹ کا سائز اور جرسی', 'Size chart': 'سائز چارٹ', 'Ask players for their sizes': 'کھلاڑیوں سے سائز پوچھیں', 'Copy the list': 'فہرست کاپی کریں', 'Chest × Length': 'سینہ × لمبائی',

    // weather
    'Clear sky': 'صاف آسمان', 'Mostly clear': 'زیادہ تر صاف', 'Partly cloudy': 'جزوی ابر آلود', 'Overcast': 'ابر آلود', 'Foggy': 'دھند',
    'Drizzle': 'ہلکی بوندا باندی', 'Rain': 'بارش', 'Snow': 'برف باری', 'Rain showers': 'بارش کی بوچھاڑ', 'Thunderstorm': 'آندھی طوفان',
    'Rain likely': 'بارش کا امکان', 'Weather': 'موسم',

    // match, fixtures, next match
    'Match': 'میچ', 'Matches': 'میچ', 'match': 'میچ', 'matches': 'میچ', 'Training': 'ٹریننگ', 'Trainings': 'ٹریننگز',
    'Trainings & matches': 'ٹریننگز اور میچ', 'Upcoming': 'آنے والے', 'Played': 'کھیلے گئے', 'Fixtures': 'میچوں کا شیڈول',
    'Opponent': 'مخالف ٹیم', 'Opponent TBC': 'مخالف ٹیم طے ہونا باقی', 'Venue': 'مقام', 'Match date': 'میچ کی تاریخ', 'Match day': 'میچ کا دن',
    'Next match card': 'اگلے میچ کا کارڈ', 'Directions': 'راستہ', 'See details': 'تفصیل دیکھیں', 'About match': 'میچ کے بارے میں',
    'Squad': 'اسکواڈ', 'Red Falcons squad': 'ریڈ فالکنز اسکواڈ', 'Playing XI': 'پلیئنگ الیون', 'XI announced': 'الیون کا اعلان ہو گیا',
    'XI not announced yet': 'الیون کا اعلان ابھی نہیں ہوا', 'Watch this space before match day': 'میچ سے پہلے یہاں دیکھتے رہیں',
    'The Playing XI will be announced before match day.': 'پلیئنگ الیون کا اعلان میچ کے دن سے پہلے کیا جائے گا۔',
    'Batting order': 'بیٹنگ آرڈر', '12th man': 'بارہواں کھلاڑی', 'Scorecard': 'اسکور کارڈ', 'Batter': 'بلے باز',
    'Photos': 'تصاویر', 'Photo': 'تصویر', 'Match photos': 'میچ کی تصاویر', 'Add photos': 'تصاویر شامل کریں', 'Remove': 'ہٹائیں',
    'Previous': 'پچھلی', 'Next': 'اگلی', 'Upload photo': 'تصویر اپ لوڈ کریں', 'Change photo': 'تصویر بدلیں',
    'No photos yet. Be the first to add one.': 'ابھی کوئی تصویر نہیں۔ پہلی تصویر آپ لگائیں۔',
    'Date TBC': 'تاریخ طے ہونا باقی', 'Today': 'آج', 'Tomorrow': 'کل', 'Awaiting result': 'نتیجے کا انتظار',
    'Time to be confirmed': 'وقت طے ہونا باقی', 'days': 'دن', 'hours': 'گھنٹے', 'mins': 'منٹ', 'secs': 'سیکنڈ',
    'Day': 'دن', 'Hours': 'گھنٹے', 'Mins': 'منٹ', 'Secs': 'سیکنڈ', 'VS': 'بمقابلہ', 'Playing': 'کھیل رہے ہیں',
    'Kick-off time (leave empty until confirmed)': 'میچ شروع ہونے کا وقت (طے ہونے تک خالی رکھیں)',
    'Kick-off time — leave empty until it is confirmed': 'میچ شروع ہونے کا وقت — طے ہونے تک خالی رکھیں',
    'Set it on the Days tab': 'دن کے ٹیب میں طے کریں', 'Add match': 'میچ شامل کریں', 'Add training': 'ٹریننگ شامل کریں',
    'Add 5 test matches': '5 ٹیسٹ میچ شامل کریں', 'Test matches': 'ٹیسٹ میچ', 'Finished · charged': 'مکمل · چارج ہو گیا',
    'Not charged yet': 'ابھی چارج نہیں ہوا', 'Season sheet': 'سیزن شیٹ', 'Attendance': 'حاضری', 'Attended': 'حاضر', 'Arrived': 'پہنچ گئے',
    'Not arrived': 'نہیں پہنچے', 'Did not play': 'نہیں کھیلے', 'Taking part': 'شریک', 'Taking part in the league': 'لیگ میں شریک',
    'arrived': 'پہنچے', '0 arrived': '0 پہنچے',

    // summary & money
    'Balance': 'بیلنس', 'Balance due': 'واجب الادا رقم', 'Your balance': 'آپ کا بیلنس', 'Everyone’s balances': 'سب کے بیلنس',
    'Players still to pay': 'ادائیگی باقی والے کھلاڑی', 'Players in credit': 'جن کے پیسے زائد ہیں', 'Players who owe money': 'جن پر رقم واجب ہے',
    'Tap a player for the full breakdown.': 'مکمل تفصیل کے لیے کھلاڑی پر ٹیپ کریں۔',
    'Share as image': 'تصویر کے طور پر شیئر کریں', 'Collected': 'جمع شدہ', 'Total spent': 'کل خرچ', 'spent': 'خرچ',
    'Still owed to management': 'انتظامیہ کو ابھی ادا کرنا باقی', 'Still owed to you': 'آپ کو ابھی ملنا باقی', 'To be paid back': 'واپس ادا کرنا ہے',
    'You pay back': 'آپ نے واپس ادا کرنا ہے', 'Settled': 'حساب برابر', 'Paid': 'ادا شدہ', 'Cost': 'لاگت', 'Bought': 'خریدا',
    'Paid in by players': 'کھلاڑیوں کی ادائیگیاں', 'Payments to management': 'انتظامیہ کو ادائیگیاں', '+ Add payment to management': '+ انتظامیہ کو ادائیگی شامل کریں',
    'I have paid management': 'میں نے انتظامیہ کو ادا کر دیا', 'In my account': 'میرے اکاؤنٹ میں', 'Items bought for the team': 'ٹیم کے لیے خریدی گئی چیزیں',
    'Expenses': 'اخراجات', 'Expenses & attendance': 'اخراجات اور حاضری', 'Expenses and payments': 'اخراجات اور ادائیگیاں',
    'No expenses yet': 'ابھی کوئی خرچ نہیں', 'Day expenses': 'دن کے اخراجات', 'Description (e.g. Nets at club)': 'تفصیل (مثلاً کلب میں نیٹس)',
    'Add:': 'شامل کریں:', '+ Ground fee': '+ گراؤنڈ فیس', '+ Balls': '+ گیندیں', '+ Water': '+ پانی', '+ Other': '+ دیگر',
    'League': 'لیگ', 'League · whole series': 'لیگ · پوری سیریز', 'League fee': 'لیگ فیس', 'League matches': 'لیگ کے میچ',
    'League name': 'لیگ کا نام', 'Advance each': 'ہر ایک کا ایڈوانس', 'Advance per player': 'فی کھلاڑی ایڈوانس',
    'Each player per match': 'ہر کھلاڑی فی میچ', 'Total for the series': 'پوری سیریز کا کل', 'No advance asked': 'ایڈوانس نہیں مانگا گیا',
    'Set the league fee': 'لیگ فیس طے کریں', 'Tournament': 'ٹورنامنٹ', 'New series': 'نئی سیریز', 'Still owed to mgmt': 'انتظامیہ کو باقی',
    'Matches played': 'کھیلے گئے میچ', 'Matches the league fee covers': 'وہ میچ جو لیگ فیس میں شامل ہیں', 'Minimise matches': 'میچ چھوٹے کریں',
    'Minimise trainings': 'ٹریننگز چھوٹی کریں', 'Summary cards': 'خلاصہ کارڈ', 'each': 'ہر ایک',
    'Collected − sent to management − expenses': 'جمع شدہ − انتظامیہ کو بھیجا − اخراجات',
    'League fee for the matches each player played': 'ہر کھلاڑی کے کھیلے گئے میچوں کی لیگ فیس',
    'Each day’s expenses are split among the players who attended. Whoever paid for an item gets it credited.': 'ہر دن کے اخراجات حاضر کھلاڑیوں میں برابر تقسیم ہوتے ہیں۔ جس نے چیز کے پیسے دیے اسے وہ رقم واپس ملتی ہے۔',
    'Each match is paid by the XI who played it, not the 12th man. Matches not played yet are shared by everyone taking part.': 'ہر میچ کی فیس وہ الیون دیتی ہے جس نے میچ کھیلا، بارہواں کھلاڑی نہیں۔ جو میچ ابھی نہیں کھیلے گئے وہ سب شریک کھلاڑیوں میں بٹتے ہیں۔',
    'Round shares up': 'حصے اوپر گول کریں', 'Whole numbers, rounded up': 'پورے عدد، اوپر گول',

    // players & profile
    'Player': 'کھلاڑی', 'players': 'کھلاڑی', 'player': 'کھلاڑی', 'Name': 'نام', 'Role': 'کردار', 'Roles': 'کردار', 'No role': 'کوئی کردار نہیں', 'Batsman': 'بیٹسمین', 'Bowler': 'باؤلر',
    'All-rounder': 'آل راؤنڈر', 'Wicket-keeper': 'وکٹ کیپر', 'Batsman + Captain': 'بیٹسمین + کپتان', 'Captain': 'کپتان', 'Vice-captain': 'نائب کپتان',
    'Jersey number': 'جرسی نمبر', 'Batting hand': 'بیٹنگ کا ہاتھ', 'Bowling arm': 'باؤلنگ کا ہاتھ', 'Bowling style': 'باؤلنگ کا انداز',
    'Right-hand': 'دایاں ہاتھ', 'Left-hand': 'بایاں ہاتھ', 'Right-arm': 'دائیں ہاتھ سے', 'Left-arm': 'بائیں ہاتھ سے', 'Fast': 'تیز', 'Medium': 'درمیانی',
    'Medium-fast': 'درمیانی تیز', 'Off spin': 'آف اسپن', 'Leg spin': 'لیگ اسپن', 'Left-arm spin': 'بائیں ہاتھ کی اسپن', 'Not set': 'طے نہیں',
    'Edit profile': 'پروفائل میں ترمیم', 'My profile': 'میری پروفائل', 'Add players': 'کھلاڑی شامل کریں', 'Add to squad': 'اسکواڈ میں شامل کریں',
    'Paste names separated by new lines or commas.': 'نام نئی لائنوں یا کاما سے الگ کر کے پیسٹ کریں۔', 'No players yet.': 'ابھی کوئی کھلاڑی نہیں۔',
    'Clear': 'صاف کریں', 'Clear all': 'سب صاف کریں', 'Photo by Admin': 'ایڈمن کی تصویر', 'Your photo and cricket details.': 'آپ کی تصویر اور کرکٹ کی تفصیل۔',
    'Shown on your player profile for everyone in the team. Changes save as soon as you pick them.': 'ٹیم میں سب کو آپ کی پروفائل پر نظر آتا ہے۔ منتخب کرتے ہی تبدیلیاں محفوظ ہو جاتی ہیں۔',

    // stats
    'Batting': 'بیٹنگ', 'Bowling': 'باؤلنگ', 'Runs': 'رنز', 'Balls': 'گیندیں', 'Fours': 'چوکے', 'Sixes': 'چھکے', 'Wickets': 'وکٹیں', 'Overs': 'اوورز',
    'Catches': 'کیچ', 'Total': 'کل', 'Extras': 'ایکسٹرا', 'Did not bat': 'بیٹنگ نہیں کی', 'Strike rate': 'اسٹرائیک ریٹ', 'Economy': 'اکانومی',
    'Average': 'اوسط', 'Not out': 'ناٹ آؤٹ', 'out': 'آؤٹ', 'not out': 'ناٹ آؤٹ', 'Stats view': 'اعداد و شمار کا منظر', 'Sr.': 'نمبر',
    'No stats yet. Pick a match above and add them as you play — the season totals build up here.': 'ابھی کوئی اعداد و شمار نہیں۔ اوپر سے میچ چنیں اور کھیلتے ہوئے شامل کریں — سیزن کے کل یہاں بنتے جائیں گے۔',
    'Red Falcons innings': 'ریڈ فالکنز کی اننگز', 'Red Falcons bowling': 'ریڈ فالکنز کی باؤلنگ', 'Bowler ': 'باؤلر',

    // announcements, notifications
    'Announcements': 'اعلانات', 'New announcement': 'نیا اعلان', 'Templates': 'ٹیمپلیٹس', 'Title': 'عنوان', 'Message': 'پیغام', 'Send': 'بھیجیں',
    'Send now': 'ابھی بھیجیں', 'Send later': 'بعد میں بھیجیں', 'Sent': 'بھیجے گئے', 'Send to': 'کسے بھیجیں', 'Everyone': 'سب',
    'Nothing sent yet.': 'ابھی کچھ نہیں بھیجا گیا۔', 'Match reminder': 'میچ کی یاد دہانی', 'Day before': 'ایک دن پہلے', 'Report time': 'رپورٹ کا وقت',
    'Match update': 'میچ اپ ڈیٹ', 'Playing XI announced': 'پلیئنگ الیون کا اعلان', 'Match tomorrow': 'میچ کل ہے', 'Announcement': 'اعلان',
    'No particular match': 'کوئی خاص میچ نہیں', 'What should the team know?': 'ٹیم کو کیا بتانا ہے؟', 'e.g. Match reminder': 'مثلاً میچ کی یاد دہانی',
    'Updates': 'اپ ڈیٹس', 'Send a notification': 'اطلاع بھیجیں', 'Nothing waiting. You’re all caught up.': 'کچھ باقی نہیں۔ آپ سب دیکھ چکے ہیں۔',
    'Nothing new yet. Announcements and new expenses will show up here.': 'ابھی کچھ نیا نہیں۔ اعلانات اور نئے اخراجات یہاں نظر آئیں گے۔',
    'Announcements from the team, the Playing XI, and expenses the admin records.': 'ٹیم کے اعلانات، پلیئنگ الیون اور ایڈمن کے درج کردہ اخراجات۔',
    'Write a message for the team. It shows in everyone’s notifications, and goes to the phones that have notifications on.': 'ٹیم کے لیے پیغام لکھیں۔ یہ سب کی اطلاعات میں دکھائی دیتا ہے اور جن فونز پر اطلاعات آن ہیں ان پر بھی جاتا ہے۔',
    'Start from a template, or write your own. Pick a match to fill in its details.': 'ٹیمپلیٹ سے شروع کریں یا خود لکھیں۔ تفصیل بھرنے کے لیے میچ چنیں۔',
    'Duplicate one to reuse it, for example for the next match.': 'دوبارہ استعمال کے لیے کسی کی نقل بنائیں، مثلاً اگلے میچ کے لیے۔',
    'Never miss the team news': 'ٹیم کی خبر کبھی مت چھوڑیں', 'Turn on': 'آن کریں', 'Not now': 'ابھی نہیں', 'Turn on notifications': 'اطلاعات آن کریں',
    'Turn off': 'بند کریں', 'This device': 'یہ ڈیوائس', 'Get match updates and payment reminders as notifications.': 'میچ اپ ڈیٹس اور ادائیگی کی یاد دہانیاں اطلاعات کی صورت میں حاصل کریں۔',

    // settings
    'Team & payments': 'ٹیم اور ادائیگیاں', 'Preferences': 'ترجیحات', 'Appearance': 'ظاہری شکل', 'Theme': 'تھیم', 'Security': 'سیکیورٹی', 'Data': 'ڈیٹا',
    'Backups': 'بیک اپ', 'Clean up': 'صفائی', 'Danger zone': 'خطرناک حصہ', 'Sections': 'حصے', 'Settings sections': 'ترتیبات کے حصے',
    'Who can see what': 'کون کیا دیکھ سکتا ہے', 'Viewer access': 'دیکھنے والوں کی رسائی', 'Tabs for players': 'کھلاڑیوں کے لیے ٹیب',
    'Shown to players': 'کھلاڑیوں کو دکھایا جاتا ہے', 'Payment account': 'ادائیگی کا اکاؤنٹ', 'Bank / wallet': 'بینک / والیٹ', 'Account title': 'اکاؤنٹ کا نام',
    'Account #': 'اکاؤنٹ نمبر', 'Account or IBAN': 'اکاؤنٹ یا آئی بی اے این', 'Name on the account': 'اکاؤنٹ پر نام', 'Currency': 'کرنسی',
    'Max players per match': 'فی میچ زیادہ سے زیادہ کھلاڑی', 'Install on phone': 'فون پر انسٹال کریں', 'Install app': 'ایپ انسٹال کریں',
    'Add to Home screen': 'ہوم اسکرین میں شامل کریں', 'App managers': 'ایپ مینیجرز', 'App manager': 'ایپ مینیجر', 'No managers yet.': 'ابھی کوئی مینیجر نہیں۔',
    'Request manager access': 'مینیجر رسائی کی درخواست', 'With the manager': 'مینیجر کے پاس', 'Two-factor sign-in': 'دو مرحلوں والا سائن اِن',
    'Set up two-factor': 'دو مرحلوں کی تصدیق لگائیں', 'Protect the admin sign-in.': 'ایڈمن کے سائن اِن کو محفوظ بنائیں۔',
    'Save backup': 'بیک اپ محفوظ کریں', 'Open backup': 'بیک اپ کھولیں', 'Season archive': 'سیزن آرکائیو', 'Season name': 'سیزن کا نام',
    'Archive current season': 'موجودہ سیزن آرکائیو کریں', 'No archived seasons yet.': 'ابھی کوئی آرکائیو سیزن نہیں۔',
    'Clear all stats': 'تمام اعداد و شمار صاف کریں', 'Clear all playing XIs': 'تمام پلیئنگ الیون صاف کریں', 'Clear everything': 'سب کچھ صاف کریں',
    'These can’t be undone — save a backup first.': 'انہیں واپس نہیں کیا جا سکتا — پہلے بیک اپ محفوظ کریں۔',
    'Removes all players and data. No archive is made.': 'تمام کھلاڑی اور ڈیٹا ہٹا دیتا ہے۔ کوئی آرکائیو نہیں بنتا۔',
    'Team, money, access and data — grouped by what they change.': 'ٹیم، رقم، رسائی اور ڈیٹا — اس لحاظ سے گروپ کیے گئے کہ وہ کیا بدلتے ہیں۔',
    'Your view of the Red Falcons ledger.': 'ریڈ فالکنز لیجر کا آپ کا منظر۔', 'Where players pay and how shares are worked out.': 'کھلاڑی کہاں ادا کرتے ہیں اور حصے کیسے نکلتے ہیں۔',
    'Where to send your payments.': 'اپنی ادائیگیاں کہاں بھیجیں۔', 'Changes the look on this device only.': 'صرف اس ڈیوائس کی شکل بدلتا ہے۔',
    'How the app looks on this device.': 'اس ڈیوائس پر ایپ کیسی نظر آتی ہے۔',
    'What players see when they sign in with their name.': 'کھلاڑی اپنے نام سے سائن اِن کریں تو کیا دیکھتے ہیں۔',
    'Choose which tabs view-only players can open. You always see all of them.': 'چنیں کہ صرف دیکھنے والے کھلاڑی کون سے ٹیب کھول سکتے ہیں۔ آپ کو سب نظر آتے ہیں۔',
    'Show or hide these for all players. When they’re hidden from players you still see them, marked “Hidden from players”.': 'سب کھلاڑیوں کے لیے انہیں دکھائیں یا چھپائیں۔ چھپے ہونے پر بھی آپ کو یہ نظر آتے ہیں، “کھلاڑیوں سے چھپا ہوا” کے نشان کے ساتھ۔',
    'Hidden from players': 'کھلاڑیوں سے چھپا ہوا', 'Show Days to players': 'کھلاڑیوں کو دن دکھائیں', 'Show Stats to players': 'کھلاڑیوں کو اعداد و شمار دکھائیں',
    'Show Next match card to players': 'کھلاڑیوں کو اگلے میچ کا کارڈ دکھائیں', 'Show Fixtures calendar to players': 'کھلاڑیوں کو میچوں کا شیڈول دکھائیں',
    'Viewers see just their balance and payments': 'دیکھنے والے صرف اپنا بیلنس اور ادائیگیاں دیکھتے ہیں',
    'Viewers see the whole team’s money': 'دیکھنے والے پوری ٹیم کی رقم دیکھتے ہیں',
    'Players pick their name to sign in, so this keeps balances out of casual view — it isn’t a password lock.': 'کھلاڑی نام چن کر سائن اِن کرتے ہیں، اس لیے یہ بیلنس کو عام نظر سے دور رکھتا ہے — یہ پاس ورڈ کا تالا نہیں ہے۔',
    'Only their own': 'صرف اپنا', 'Can change everything': 'سب کچھ بدل سکتا ہے', 'Only you can open it': 'صرف آپ کھول سکتے ہیں',

    // themes
    'Night': 'رات', 'Daylight': 'دن کی روشنی', 'Pitch Green': 'پچ گرین', 'Midnight Blue': 'مڈنائٹ بلو', 'Royal Purple': 'رائل پرپل', 'Sunset': 'غروب آفتاب',
    'Charcoal, falcon red and gold': 'چارکول، فالکن ریڈ اور سنہری', 'Light, for reading outdoors': 'ہلکا، باہر پڑھنے کے لیے',
    'Cricket-field green and gold': 'کرکٹ میدان کا سبز اور سنہری', 'Deep navy, royal blue and gold': 'گہرا نیوی، شاہی نیلا اور سنہری',
    'Dark plum, violet and gold': 'گہرا آلو بخارا، بنفشی اور سنہری', 'Warm dusk, sunset orange and gold': 'گرم شام، غروب کا نارنجی اور سنہری',

    // dialogs
    'Cancel': 'منسوخ کریں', 'Confirm': 'تصدیق', 'Delete': 'حذف کریں', 'Save': 'محفوظ کریں', 'Duplicate': 'نقل بنائیں', 'Edit': 'ترمیم', 'Done': 'مکمل',
    'Saved': 'محفوظ ہو گیا', 'Saving…': 'محفوظ ہو رہا ہے…', 'Notifications, ': 'اطلاعات، ', 'Language': 'زبان', 'اردو': 'اردو', 'English': 'English',
    'Remove this photo?': 'یہ تصویر ہٹائیں؟', 'It will be taken down for everyone.': 'یہ سب کے لیے ہٹا دی جائے گی۔', 'Photo removed.': 'تصویر ہٹا دی گئی۔',
    'Photo added.': 'تصویر شامل ہو گئی۔', 'Uploading photo…': 'تصویر اپ لوڈ ہو رہی ہے…', 'Photo updated.': 'تصویر اپ ڈیٹ ہو گئی۔'
  };

  const PATTERNS = [
    [/^(\d+) players? · (\d+) trainings? · (\d+) match(?:es)?$/, (m) => `${m[1]} کھلاڑی · ${m[2]} ٹریننگ · ${m[3]} میچ`],
    [/^(-?\d+)° \/ (-?\d+)° · (\d+|—)% chance of rain · wind (\d+) km\/h$/, (m) => `${m[1]}° / ${m[2]}° · بارش کا امکان ${m[3]}% · ہوا ${m[4]} کلومیٹر فی گھنٹہ`],
    [/^Forecast for (.+)$/, (m) => `${m[1]} کا موسم`],
    [/^(\d+) players?$/, (m) => `${m[1]} کھلاڑی`],
    [/^(\d+) in the squad$/, (m) => `اسکواڈ میں ${m[1]}`],
    [/^(\d+) of (\d+) matches? played$/, (m) => `${m[2]} میں سے ${m[1]} میچ کھیلے گئے`],
    [/^(\d+) of (\d+) played$/, (m) => `${m[2]} میں سے ${m[1]} کھیلے`],
    [/^(\d+) \/ (\d+) played$/, (m) => `${m[2]} میں سے ${m[1]} کھیلے`],
    [/^(\d+) arrived$/, (m) => `${m[1]} پہنچے`],
    [/^In (\d+) days?$/, (m) => `${m[1]} دن میں`],
    [/^(\d+) photos? from the matches\. Tap one to open it\.$/, (m) => `میچوں کی ${m[1]} تصاویر۔ کھولنے کے لیے ٹیپ کریں۔`],
    [/^(.+) updated their profile(.*)$/, (m) => `${m[1]} نے اپنی پروفائل اپ ڈیٹ کی${m[2]}`],
    [/^Profile request · (\d+) of (\d+) done$/, (m) => `پروفائل کی درخواست · ${m[2]} میں سے ${m[1]} مکمل`],
    [/^(\d+) of (\d+) players have updated their profile\. Tap a name to see it\.$/, (m) => `${m[2]} میں سے ${m[1]} کھلاڑیوں نے پروفائل اپ ڈیٹ کی ہے۔ دیکھنے کے لیے نام پر ٹیپ کریں۔`],
    [/^Updated (.+) ago$/, (m) => `اپ ڈیٹ: ${tr(m[1] + ' ago')}`],
    [/^Match (\d+)$/, (m) => `میچ ${m[1]}`],
    [/^Training (\d+)$/, (m) => `ٹریننگ ${m[1]}`],
    [/^Test (\d+)$/, (m) => `ٹیسٹ ${m[1]}`],
    [/^Match (\d+) XI \+ 12th$/, (m) => `میچ ${m[1]} الیون + بارہواں کھلاڑی`],
    [/^Next match · (.+)$/, (m) => `اگلا میچ · ${tr(m[1])}`],
    [/^vs (.+)$/, (m) => `بمقابلہ ${m[1]}`],
    [/^vs (.+) · (.+)$/, (m) => `بمقابلہ ${m[1]} · ${tr(m[2])}`],
    [/^Red Falcons v (.+)$/, (m) => `ریڈ فالکنز بمقابلہ ${m[1]}`],
    [/^Expense card for (.+)$/, (m) => `${m[1]} کا خرچ کارڈ`],
    [/^Role for (.+)$/, (m) => `${m[1]} کا کردار`],
    [/^Remove (.+)$/, (m) => `${m[1]} ہٹائیں`],
    [/^Photo by (.+)$/, (m) => `${m[1]} کی تصویر`],
    [/^Added by (.+) · (.+)$/, (m) => `${m[1]} نے شامل کی · ${tr(m[2])}`],
    [/^Photo · (\d+) of (\d+)$/, (m) => `تصویر · ${m[1]} از ${m[2]}`],
    [/^Match (\d+): (.*)$/, (m) => `میچ ${m[1]}: ${m[2]}`],
    [/^(.+) · view only$/, (m) => `${m[1]} · صرف دیکھنے والا`],
    [/^(.+) · manager$/, (m) => `${m[1]} · مینیجر`],
    [/^Updated (.+)$/, (m) => `اپ ڈیٹ: ${tr(m[1])}`],
    [/^(\d+) requests? waiting\./, (m) => `${m[1]} درخواست انتظار میں۔`],
    [/^To: (.+)$/, (m) => `کسے: ${m[1] === 'everyone with notifications on' ? 'اطلاعات آن رکھنے والے سب' : m[1]}`],
    [/^just now$/, () => 'ابھی ابھی'],
    [/^(\d+) (min|mins|minute|minutes) ago$/, (m) => `${m[1]} منٹ پہلے`],
    [/^(\d+) (hour|hours|h) ago$/, (m) => `${m[1]} گھنٹے پہلے`],
    [/^(\d+) (day|days|d) ago$/, (m) => `${m[1]} دن پہلے`]
  ];

  // A date-ish line: swap weekday and month names for Urdu, leaving digits alone.
  function trDate(s) {
    if (!/\b(Mon|Tue|Wed|Thu|Fri|Sat|Sun|Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\b|\d ?[ap]m\b/.test(s)) return null;
    let changed = false;
    const out = s.replace(/\b(Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday|January|February|March|April|June|July|August|September|October|November|December|Mon|Tue|Wed|Thu|Fri|Sat|Sun|Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sept|Sep|Oct|Nov|Dec)\b/g,
      w => { const u = WEEKDAYS[w] || MONTHS[w]; if (u) { changed = true; return u; } return w; })
      .replace(/(\d{1,2}:\d{2}) ?am\b/g, (_, t) => { changed = true; return `${t} صبح`; })
      .replace(/(\d{1,2}:\d{2}) ?pm\b/g, (_, t) => { changed = true; return `${t} شام`; });
    return changed ? out.replace(/,/g, '،') : null;
  }

  function tr(text) {
    if (!text) return text;
    const lead = text.match(/^\s*/)[0], trail = text.match(/\s*$/)[0];
    const core = text.trim().replace(/\s+/g, ' ');
    if (!core) return text;
    let out = DICT[core];
    if (out == null) {
      for (const [re, fn] of PATTERNS) {
        const m = core.match(re);
        if (m) { out = fn(m); break; }
      }
    }
    if (out == null) out = trDate(core);
    if (out == null && core.includes(' · ')) {
      // "Match 2 · Saturday, 17 October 2026": translate each part.
      const parts = core.split(' · ').map(p => tr(p));
      if (parts.some((p, i) => p !== core.split(' · ')[i])) out = parts.join(' · ');
    }
    return out == null ? text : lead + out + trail;
  }

  const ATTRS = ['placeholder', 'aria-label', 'title', 'alt'];
  function walk(node) {
    if (node.nodeType === 3) {
      const p = node.parentElement;
      if (p && !/^(SCRIPT|STYLE|TEXTAREA)$/.test(p.tagName)) {
        const t = tr(node.nodeValue);
        if (t !== node.nodeValue) node.nodeValue = t;
      }
      return;
    }
    if (node.nodeType !== 1 || /^(SCRIPT|STYLE)$/.test(node.tagName)) return;
    for (const a of ATTRS) {
      const v = node.getAttribute(a);
      if (v) { const t = tr(v); if (t !== v) node.setAttribute(a, t); }
    }
    node.childNodes.forEach(walk);
  }

  const I18N = {
    lang,
    tr: s => (lang === 'ur' ? tr(s) : s),
    set(next) {
      try { localStorage.setItem(KEY, JSON.stringify(next === 'ur' ? 'ur' : 'en')); } catch { /* ignore */ }
      location.reload();
    },
    toggle() { I18N.set(lang === 'ur' ? 'en' : 'ur'); }
  };
  window.I18N = I18N;

  if (lang !== 'ur') return;
  document.documentElement.lang = 'ur';
  document.documentElement.dir = 'rtl';

  let queue = [];
  let scheduled = false;
  const observer = new MutationObserver(records => {
    for (const r of records) {
      if (r.type === 'characterData') queue.push(r.target);
      else r.addedNodes.forEach(n => queue.push(n));
    }
    if (!scheduled) { scheduled = true; Promise.resolve().then(flush); }
  });
  function flush() {
    scheduled = false;
    const work = queue; queue = [];
    work.forEach(n => { if (n.isConnected) walk(n); });
    observer.takeRecords();   // our own edits are not news
  }
  function start() {
    walk(document.body);
    observer.observe(document.body, { childList: true, subtree: true, characterData: true });
  }
  if (document.body) start(); else document.addEventListener('DOMContentLoaded', start);
})();
