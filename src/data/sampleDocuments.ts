export enum DocumentFormat {
  PDF = 'PDF',
  WORD = 'DOCX',
  IMAGE = 'PIC',
  TEXT = 'TXT',
}

export enum ParagraphStyle {
  TITLE = 'TITLE',
  HEADING = 'HEADING',
  SUBHEADING = 'SUBHEADING',
  BODY = 'BODY',
  QUOTE = 'QUOTE',
  LIST_ITEM = 'LIST_ITEM',
}

export interface DocumentParagraph {
  index: number;
  text: string;
  style: ParagraphStyle;
  pageNumber: number;
}

export interface HighlightAnnotation {
  id: string;
  docId: string;
  paragraphIndex: number;
  startOffset: number;
  endOffset: number;
  selectedText: string;
  colorHex: string;
  createdAt: string;
}

export interface ReaderDocument {
  id: string;
  title: string;
  subtitle: string;
  author: string;
  format: DocumentFormat;
  formatLabelFa: string;
  dateAdded: string;
  fileSize: string;
  pageCount: number;
  paragraphs: DocumentParagraph[];
  imageUrl?: string;
}

export const INITIAL_DOCUMENTS: ReaderDocument[] = [
  {
    id: 'doc_pdf_persian_arabic',
    title: 'راهنمای جامع متون فارسی و عربی و تایپوگرافی دیجیتال',
    subtitle: 'بررسی ساختار حروف، اعراب‌گذاری، نیم‌فاصله و استانداردهای یونیکد',
    author: 'دکتر محمدرضا شفیعی و پژوهشگران نشر دیجیتال',
    format: DocumentFormat.PDF,
    formatLabelFa: 'سند PDF',
    dateAdded: '۱۴۰۴/۰۸/۰۷',
    fileSize: '۲.۴ MB',
    pageCount: 3,
    paragraphs: [
      {
        index: 0,
        text: 'راهنمای جامع پردازش متون فارسی و عربی در اسناد دیجیتال',
        style: ParagraphStyle.TITLE,
        pageNumber: 1,
      },
      {
        index: 1,
        text: 'فصل اول: یکسان‌سازی حروف فارسی و عربی (ک/ك و ی/ي)',
        style: ParagraphStyle.HEADING,
        pageNumber: 1,
      },
      {
        index: 2,
        text: 'یکی از مهم‌ترین چالش‌ها در جستجوی اسناد PDF و فایل‌های Word، تفاوت کدهای یونیکد میان حروف فارسی و عربی است. برای نمونه، در بسیاری از متون قدیمی یا عربی از حرف «ك» (کاف عربی) و «ي» یا «ى» (یای عربی و الف مقصوره) در کلماتی همچون «کتابخانه»، «کیمیا»، «علی»، «مصطفى» و «مسئله» استفاده می‌شود. موتور جستجوی هوشمند این برنامه به گونه‌ای طراحی شده است که با تایپ «کتاب» یا «علی» با کیبورد فارسی، تمام اشکال «كتاب»، «کتاب»، «علي»، «علی» و «على» را بلافاصله می‌یابد.',
        style: ParagraphStyle.BODY,
        pageNumber: 1,
      },
      {
        index: 3,
        text: 'بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ — إِنَّا فَتَحْنَا لَكَ فَتْحًا مُبِينًا. در متون دارای اعراب كامل (فتحه، كسره، ضمه، تشديد و تنوين) مانند آیات قرآن کریم، نهج‌البلاغه و متون ادب فارسی همچون گلستان سعدی و دیوان حافظ، کاربر می‌تواند بدون نیاز به تایپ حرکات و اعراب، تنها با نوشتن «بسم الله الرحمن الرحیم» یا «انا فتحنا لک فتحا مبینا»، عبارت دقیق را به همراه تمام اعراب آن در متن پیدا کند.',
        style: ParagraphStyle.QUOTE,
        pageNumber: 1,
      },
      {
        index: 4,
        text: 'فصل دوم: مدیریت هوشمند نیم‌فاصله (ZWNJ) و پیشوندهای فعلی',
        style: ParagraphStyle.HEADING,
        pageNumber: 2,
      },
      {
        index: 5,
        text: 'در زبان فارسی، افعال استمراری و جمع‌ها اغلب با نیم‌فاصله نوشته می‌شوند؛ مانند «می‌شود»، «نمی‌دانند»، «کتاب‌ها»، «پژوهش‌های علمی» و «برنامه‌نویسی». با این حال، بسیاری از کاربران هنگام جستجو روی گوشی موبایل یا رایانه از فاصله معمولی استفاده می‌کنند («می شود» یا «میشود»). موتور جستجوی پارس‌ریدر هر سه حالت «می‌شود»، «می شود» و «میشود» را معادل هم تشخیص داده و هایلایت دقیق روی کلمه اصلی ایجاد می‌کند.',
        style: ParagraphStyle.BODY,
        pageNumber: 2,
      },
      {
        index: 6,
        text: 'همچنین انواع الف و همزه شامل «آ»، «ا»، «أ»، «إ» و «ؤ» در واژگانی نظیر «آسمان»، «أحمد»، «إحسان»، «مسؤولیت» و «مؤمن» و نیز «ة» (تاء گرد عربی) و «ه» در کلماتی چون «حکمة / حکمت / حکمه» و «مدینة / مدینه» به‌صورت یکپارچه پشتیبانی می‌شوند.',
        style: ParagraphStyle.BODY,
        pageNumber: 2,
      },
      {
        index: 7,
        text: 'فصل سوم: حل مشکل پرش کرسر در انتخاب و کپی متن (Smart RTL Cursor)',
        style: ParagraphStyle.HEADING,
        pageNumber: 3,
      },
      {
        index: 8,
        text: 'در بسیاری از برنامه‌های مطالعه، هنگام کشیدن دستگیره انتخاب متن (Cursor Handle) روی متون راست‌به‌چپ فارسی، به محض خروج جزئی انگشت از کادر خط، کرسر دچار پرش ناگهانی شده و کل پاراگراف‌های بالایی یا تمام صفحه را انتخاب می‌کند! در این برنامه با طراحی موتور انتخاب اختصاصی تک‌واحدی و قفل هوشمند محدوده با مختصات محلی، دستگیره شروع (راست) و دستگیره پایان (چپ) دقیقاً در همان جهتی که انگشت شما حرکت می‌کند کاراکتر به کاراکتر جلو می‌روند.',
        style: ParagraphStyle.BODY,
        pageNumber: 3,
      },
      {
        index: 9,
        text: 'برای آزمایش همین حالا روی هر کلمه از این پاراگراف کلیک یا لمس کنید و دستگیره‌های فیروزه‌ای و آبی را با انگشت یا ماوس به چپ، راست، بالا و پایین بکشید تا دقت حرکت کرسر بدون پرش به بالای صفحه را تجربه کنید.',
        style: ParagraphStyle.LIST_ITEM,
        pageNumber: 3,
      },
    ],
  },
  {
    id: 'doc_word_contract',
    title: 'قرارداد توسعه نرم‌افزار و خدمات ابری شرکت دانش‌بنیان',
    subtitle: 'فایل Word رسمی شامل بندهای حقوقی، مبالغ ارزی و ریالی و تعهدات فنی',
    author: 'واحد حقوقی و قراردادها',
    format: DocumentFormat.WORD,
    formatLabelFa: 'فایل Word',
    dateAdded: '۱۴۰۴/۰۸/۰۵',
    fileSize: '۸۴۵ KB',
    pageCount: 2,
    paragraphs: [
      {
        index: 0,
        text: 'قرارداد جامع طراحی و پیاده‌سازی سامانه هوشمند مدیریت اسناد',
        style: ParagraphStyle.TITLE,
        pageNumber: 1,
      },
      {
        index: 1,
        text: 'ماده ۱ — موضوع قرارداد و مشخصات طرفین',
        style: ParagraphStyle.HEADING,
        pageNumber: 1,
      },
      {
        index: 2,
        text: 'این قرارداد فیمابین شركت فناوری اطلاعات پارس‌گستر (با شماره ثبت ۵۴۸۹۲۰) به عنوان کارفرما از یک سو، و تیم مهندسی نرم‌افزار پیشرو به عنوان مجری از سوی دیگر، در تاریخ ۱۴۰۴/۰۸/۰۱ منعقد می‌گردد. موضوع قرارداد عبارت است از طراحی، پیاده‌سازی و پشتیبانی سامانه خوانشگر اسناد PDF، Word، تصاویر و متون با قابلیت جستجوی پیشرفته فارسی و عربی.',
        style: ParagraphStyle.BODY,
        pageNumber: 1,
      },
      {
        index: 3,
        text: 'ماده ۲ — مبلغ قرارداد و نحوه پرداخت (پشتیبانی از اعداد ۵۴۸۹۲۰ و 548920 و ٥٤٨٩٢٠)',
        style: ParagraphStyle.HEADING,
        pageNumber: 1,
      },
      {
        index: 4,
        text: 'مبلغ کل قرارداد برابر با ۲,۴۵۰,۰۰۰,۰۰۰ ریال (معادل دویست و چهل و پنج میلیون تومان) تعیین می‌گردد. مرحله اول معادل ۳۰ درصد پس از تصویب معماری فنی، مرحله دوم معادل ۴۰ درصد پس از تحویل نسخه آزمایشی، و مرحله سوم معادل ۳۰ درصد پس از تأیید نهایی کارفرما پرداخت خواهد شد.',
        style: ParagraphStyle.BODY,
        pageNumber: 1,
      },
      {
        index: 5,
        text: 'ماده ۳ — الزامات فنی و استانداردهای کیفیت رابط کاربری',
        style: ParagraphStyle.HEADING,
        pageNumber: 2,
      },
      {
        index: 6,
        text: 'الف) سامانه باید از فایل‌های متنی با کدگذاری UTF-8، اسناد ویرایشگر وُرد (DOCX)، فایل‌های پی‌دی‌اف (PDF) و تصاویر اسکن‌شده (JPG/PNG) به صورت کامل و بدون بههم‌ریختگی فونت پشتیبانی نماید.',
        style: ParagraphStyle.LIST_ITEM,
        pageNumber: 2,
      },
      {
        index: 7,
        text: 'ب) عملکرد انتخاب متن و کپی در محیط‌های لمسی باید دارای پایداری ۱۰۰٪ بوده و از پرش ناگهانی نشانگر به ابتدای سند جلوگیری کند.',
        style: ParagraphStyle.LIST_ITEM,
        pageNumber: 2,
      },
    ],
  },
  {
    id: 'doc_pic_manuscript',
    title: 'اسکن کتیبه و دست‌خط ادبی کهن (به همراه متن استخراج‌شده OCR)',
    subtitle: 'تصویر باکیفیت با قابلیت زوم و جابجایی و لایه متنی قابل جستجو و کپی',
    author: 'گنجینه اسناد خطی و تصویری',
    format: DocumentFormat.IMAGE,
    formatLabelFa: 'تصویر و عکس',
    dateAdded: '۱۴۰۴/۰۸/۰۳',
    fileSize: '۳.۸ MB',
    pageCount: 1,
    paragraphs: [
      {
        index: 0,
        text: 'متن استخراج‌شده از تصویر: گلستان سعدی و حکمت‌های کهن',
        style: ParagraphStyle.TITLE,
        pageNumber: 1,
      },
      {
        index: 1,
        text: 'بَنی‌آدَم أَعْضایِ یَکْدیگَرَند / کِه دَر آفَرینِش زِ یَک گُوهَرَند',
        style: ParagraphStyle.QUOTE,
        pageNumber: 1,
      },
      {
        index: 2,
        text: 'چُو عُضْوی بِه دَرْد آوَرَد رُوزْگار / دِگَر عُضْوها را نَمانَد قَرار — تو کَز مِحْنَتِ دیگَران بی‌غَمی / نَشایَد کِه نامَت نِهَند آدَمی.',
        style: ParagraphStyle.QUOTE,
        pageNumber: 1,
      },
      {
        index: 3,
        text: 'توضیحات تصویرشناسی: این لوح تصویری نمونه‌ای از اسناد اسکن‌شده با وضوح بالا است. شما می‌توانید در تب «نمای گرافیکی صفحه / تصویر» جزئیات گرافیکی لوح را با بزرگنمایی بررسی کنید و در تب «متن هوشمند و انتخاب دقیق»، ابیات دارای اعراب بالا را بدون تایپ اعراب (مثلاً با جستجوی «بنی ادم اعضای یکدیگرند» یا «گوهرند») جستجو و کپی نمایید.',
        style: ParagraphStyle.BODY,
        pageNumber: 1,
      },
    ],
  },
  {
    id: 'doc_txt_anthology',
    title: 'گزیده متون کلاسیک عربی و فارسی (نهج‌البلاغه و قابوس‌نامه)',
    subtitle: 'فایل متنی استاندارد برای تست جستجوی حروف عربی (ة، ي، ك، أ، إ، ؤ) و اعراب',
    author: 'مجموعه متون مرجع دیجیتال',
    format: DocumentFormat.TEXT,
    formatLabelFa: 'متن ساده',
    dateAdded: '۱۴۰۴/۰۸/۰۱',
    fileSize: '۱۹۰ KB',
    pageCount: 2,
    paragraphs: [
      {
        index: 0,
        text: 'گزیده حکمت‌های نهج‌البلاغه و متون ادب پارسی',
        style: ParagraphStyle.TITLE,
        pageNumber: 1,
      },
      {
        index: 1,
        text: 'حکمت اول: قِيمَةُ كُلِّ امْرِئٍ مَا يُحْسِنُهُ — ارزش هر انسانی به اندازه دانشی است که آن را به نیکی و مهارت انجام می‌دهد.',
        style: ParagraphStyle.QUOTE,
        pageNumber: 1,
      },
      {
        index: 2,
        text: 'حکمت دوم: الْعِلْمُ وِرَاثَةٌ كَرِيمَةٌ، وَالْآدَابُ حُلَلٌ مُجَدَّدَةٌ، وَالْفِكْرُ مِرْآةٌ صَافِيَةٌ. دانش میراثی گرانقدر است، و ادب زیورهایی نو به نو، و اندیشه آینه‌ای زلال و شفاف است. (در این بند می‌توانید کلمات «قیمه»، «کل»، «میراث»، «كریمه» یا «مرآه / مراه» را با هر املایی جستجو کنید).',
        style: ParagraphStyle.BODY,
        pageNumber: 1,
      },
      {
        index: 3,
        text: 'بخش دوم: از اندرزهای قابوس‌نامه در آیین سخنوری و دانش‌اندوزی',
        style: ParagraphStyle.HEADING,
        pageNumber: 2,
      },
      {
        index: 4,
        text: 'بدان ای پسر که مردم تا زنده بُوَد از دانش ناگزیر بُوَد؛ و از همه چیزها پرهیز شاید کردن مگر از دانش و هنر. سخن را به اندازه گوی و در هر مجلسی سخنی گوی که سزاوار آن مجلس بُوَد، تا در چشم خردمندان عزیز و گرامی باشی.',
        style: ParagraphStyle.BODY,
        pageNumber: 2,
      },
    ],
  },
];
