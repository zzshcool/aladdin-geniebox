package com.aladdin.geniebox.data.scanner

import java.text.Normalizer
import java.util.Locale

object PinyinZhuYinIndex {

    // 常用繁簡字對照字典
    private val TRAD_TO_SIMP = mapOf(
        '超' to '超', '級' to '级', '瑪' to '玛', '莉' to '莉', '歐' to '欧', '賽' to '赛', '車' to '车',
        '格' to '格', '鬥' to '斗', '冒' to '冒', '險' to '险', '勇' to '勇', '者' to '者', '惡' to '恶',
        '龍' to '龙', '太' to '太', '空' to '空', '戰' to '战', '士' to '士', '寶' to '宝', '可' to '可',
        '夢' to '梦', '精' to '精', '靈' to '灵', '神' to '神', '奇' to '奇', '貝' to '贝', '三' to '三',
        '國' to '国', '志' to '志', '洛' to '洛', '克' to '克', '人' to '人', '魔' to '魔', '城' to '城',
        '音' to '音', '速' to '速', '小' to '小', '子' to '子', '魂' to '魂', '羅' to '罗', '熱' to '热',
        '血' to '血', '快' to '快', '打' to '打', '旋' to '旋', '風' to '风', '星' to '星', '之' to '之',
        '卡' to '卡', '比' to '比', '薩' to '萨', '爾' to '尔', '達' to '达', '傳' to '传', '說' to '说',
        '生' to '生', '化' to '化', '危' to '危', '機' to '机', '合' to '合', '金' to '金', '彈' to '弹',
        '頭' to '头', '街' to '街', '最' to '最', '終' to '终', '幻' to '幻', '想' to '想', '拳' to '拳',
        '皇' to '皇', '月' to '月', '下' to '下', '夜' to '夜', '曲' to '曲', '天' to '天', '誅' to '诛',
        '古' to '古', '惑' to '惑', '狼' to '狼', '山' to '山', '脊' to '脊', '極' to '极', '品' to '品',
        '飛' to '飞', '大' to '大', '亂' to '乱', '恐' to '恐', '龍' to '龙', '島' to '岛', '幽' to '幽',
        '遊' to '游', '白' to '白', '書' to '书', '雙' to '双', '截' to '截', '忍' to '忍', '外' to '外',
        '影' to '影', '武' to '武', '劍' to '剑', '聖' to '圣', '火' to '火', '焰' to '焰', '紋' to '纹',
        '章' to '章', '重' to '重', '裝' to '装', '機' to '机', '兵' to '兵', '封' to '封', '印' to '印',
        '烈' to '烈', '火' to '火', '聖' to '圣', '魔' to '魔', '光' to '光', '之' to '之', '石' to '石',
        '黃' to '黄', '金' to '金', '太' to '太', '陽' to '阳', '漆' to '漆', '黑' to '黑', '魅' to '魅'
    )

    // 常用中文遊戲漢字真實拼音與注音聲母查表 (字 -> Pair(拼音首字母, 注音聲母))
    private val CHAR_PHONETIC_MAP = mapOf(
        '超' to Pair('c', "ㄔ"), '級' to Pair('j', "ㄐ"), '级' to Pair('j', "ㄐ"),
        '瑪' to Pair('m', "ㄇ"), '玛' to Pair('m', "ㄇ"), '莉' to Pair('l', "ㄌ"),
        '歐' to Pair('o', "ㄡ"), '欧' to Pair('o', "ㄡ"), '賽' to Pair('s', "ㄙ"),
        '赛' to Pair('s', "ㄙ"), '車' to Pair('c', "ㄔ"), '车' to Pair('c', "ㄔ"),
        '格' to Pair('g', "ㄍ"), '鬥' to Pair('d', "ㄉ"), '斗' to Pair('d', "ㄉ"),
        '冒' to Pair('m', "ㄇ"), '險' to Pair('x', "ㄒ"), '险' to Pair('x', "ㄒ"),
        '勇' to Pair('y', "ㄩ"), '者' to Pair('z', "ㄓ"), '惡' to Pair('e', "ㄜ"),
        '恶' to Pair('e', "ㄜ"), '龍' to Pair('l', "ㄌ"), '龙' to Pair('l', "ㄌ"),
        '太' to Pair('t', "ㄊ"), '空' to Pair('k', "ㄎ"), '戰' to Pair('z', "ㄓ"),
        '战' to Pair('z', "ㄓ"), '士' to Pair('s', "ㄕ"), '寶' to Pair('b', "ㄅ"),
        '宝' to Pair('b', "ㄅ"), '可' to Pair('k', "ㄎ"), '夢' to Pair('m', "ㄇ"),
        '梦' to Pair('m', "ㄇ"), '精' to Pair('j', "ㄐ"), '靈' to Pair('l', "ㄌ"),
        '灵' to Pair('l', "ㄌ"), '神' to Pair('s', "ㄕ"), '奇' to Pair('q', "ㄑ"),
        '貝' to Pair('b', "ㄅ"), '贝' to Pair('b', "ㄅ"), '三' to Pair('s', "ㄙ"),
        '國' to Pair('g', "ㄍ"), '国' to Pair('g', "ㄍ"), '志' to Pair('z', "ㄓ"),
        '洛' to Pair('l', "ㄌ"), '克' to Pair('k', "ㄎ"), '人' to Pair('r', "ㄖ"),
        '魔' to Pair('m', "ㄇ"), '城' to Pair('c', "ㄔ"), '音' to Pair('y', "ㄧ"),
        '速' to Pair('s', "ㄙ"), '小' to Pair('x', "ㄒ"), '子' to Pair('z', "ㄗ"),
        '魂' to Pair('h', "ㄏ"), '羅' to Pair('l', "ㄌ"), '罗' to Pair('l', "ㄌ"),
        '熱' to Pair('r', "ㄖ"), '热' to Pair('r', "ㄖ"), '血' to Pair('x', "ㄒ"),
        '快' to Pair('k', "ㄎ"), '打' to Pair('d', "ㄉ"), '旋' to Pair('x', "ㄒ"),
        '風' to Pair('f', "ㄈ"), '风' to Pair('f', "ㄈ"), '星' to Pair('x', "ㄒ"),
        '之' to Pair('z', "ㄓ"), '卡' to Pair('k', "ㄎ"), '比' to Pair('b', "ㄅ"),
        '薩' to Pair('s', "ㄙ"), '萨' to Pair('s', "ㄙ"), '爾' to Pair('e', "ㄦ"),
        '尔' to Pair('e', "ㄦ"), '達' to Pair('d', "ㄉ"), '达' to Pair('d', "ㄉ"),
        '傳' to Pair('c', "ㄔ"), '传' to Pair('c', "ㄔ"), '說' to Pair('s', "ㄕ"),
        '说' to Pair('s', "ㄕ"), '生' to Pair('s', "ㄕ"), '化' to Pair('h', "ㄏ"),
        '危' to Pair('w', "ㄨ"), '機' to Pair('j', "ㄐ"), '机' to Pair('j', "ㄐ"),
        '合' to Pair('h', "ㄏ"), '金' to Pair('j', "ㄐ"), '彈' to Pair('d', "ㄉ"),
        '弹' to Pair('d', "ㄉ"), '頭' to Pair('t', "ㄊ"), '头' to Pair('t', "ㄊ"),
        '街' to Pair('j', "ㄐ"), '最' to Pair('z', "ㄗ"), '終' to Pair('z', "ㄓ"),
        '终' to Pair('z', "ㄓ"), '幻' to Pair('h', "ㄏ"), '想' to Pair('x', "ㄒ"),
        '拳' to Pair('q', "ㄑ"), '皇' to Pair('h', "ㄏ"), '月' to Pair('y', "ㄩ"),
        '下' to Pair('x', "ㄒ"), '夜' to Pair('y', "ㄧ"), '曲' to Pair('q', "ㄑ"),
        '天' to Pair('t', "ㄊ"), '誅' to Pair('z', "ㄓ"), '诛' to Pair('z', "ㄓ"),
        '古' to Pair('g', "ㄍ"), '惑' to Pair('h', "ㄏ"), '狼' to Pair('l', "ㄌ"),
        '山' to Pair('s', "ㄕ"), '脊' to Pair('j', "ㄐ"), '極' to Pair('j', "ㄐ"),
        '极' to Pair('j', "ㄐ"), '品' to Pair('p', "ㄆ"), '飛' to Pair('f', "ㄈ"),
        '飞' to Pair('f', "ㄈ"), '大' to Pair('d', "ㄉ"), '亂' to Pair('l', "ㄌ"),
        '乱' to Pair('l', "ㄌ"), '恐' to Pair('k', "ㄎ"), '島' to Pair('d', "ㄉ"),
        '岛' to Pair('d', "ㄉ"), '幽' to Pair('y', "ㄧ"), '遊' to Pair('y', "ㄧ"),
        '游' to Pair('y', "ㄧ"), '白' to Pair('b', "ㄅ"), '書' to Pair('s', "ㄕ"),
        '书' to Pair('s', "ㄕ"), '雙' to Pair('s', "ㄕ"), '双' to Pair('s', "ㄕ"),
        '截' to Pair('j', "ㄐ"), '忍' to Pair('r', "ㄖ"), '外' to Pair('w', "ㄨ"),
        '影' to Pair('y', "ㄧ"), '武' to Pair('w', "ㄨ"), '劍' to Pair('j', "ㄐ"),
        '剑' to Pair('j', "ㄐ"), '聖' to Pair('s', "ㄕ"), '圣' to Pair('s', "ㄕ"),
        '火' to Pair('h', "ㄏ"), '焰' to Pair('y', "ㄧ"), '紋' to Pair('w', "ㄨ"),
        '纹' to Pair('w', "ㄨ"), '章' to Pair('z', "ㄓ"), '重' to Pair('z', "ㄓ"),
        '裝' to Pair('z', "ㄓ"), '装' to Pair('z', "ㄓ"), '兵' to Pair('b', "ㄅ"),
        '封' to Pair('f', "ㄈ"), '印' to Pair('y', "ㄧ"), '烈' to Pair('l', "ㄌ"),
        '石' to Pair('s', "ㄕ"), '黃' to Pair('h', "ㄏ"), '黄' to Pair('h', "ㄏ"),
        '陽' to Pair('y', "ㄧ"), '阳' to Pair('y', "ㄧ"), '漆' to Pair('q', "ㄑ"),
        '黑' to Pair('h', "ㄏ"), '魅' to Pair('m', "ㄇ"), '影' to Pair('y', "ㄧ"),
        '新' to Pair('x', "ㄒ"), '代' to Pair('d', "ㄉ"), '紀' to Pair('j', "ㄐ"),
        '元' to Pair('y', "ㄩ"), '王' to Pair('w', "ㄨ"), '國' to Pair('g', "ㄍ"),
        '心' to Pair('x', "ㄒ"), '跳' to Pair('t', "ㄊ"), '回' to Pair('h', "ㄏ"),
        '憶' to Pair('y', "ㄧ"), '塞' to Pair('s', "ㄙ"),
        // 擴充遊戲高頻字 (R2-10)
        '電' to Pair('d', "ㄉ"), '力' to Pair('l', "ㄌ"), '版' to Pair('b', "ㄅ"),
        '篇' to Pair('p', "ㄆ"), '修' to Pair('x', "ㄒ"), '改' to Pair('g', "ㄍ"),
        '無' to Pair('w', "ㄨ"), '敵' to Pair('d', "ㄉ"), '中' to Pair('z', "ㄓ"),
        '文' to Pair('w', "ㄨ"), '漢' to Pair('h', "ㄏ"), '全' to Pair('q', "ㄑ"),
        '集' to Pair('j', "ㄐ"), '合' to Pair('h', "ㄏ"), '輯' to Pair('j', "ㄐ"),
        '輯' to Pair('j', "ㄐ"), '原' to Pair('y', "ㄩ"), '版' to Pair('b', "ㄅ"),
        '系' to Pair('x', "ㄒ"), '列' to Pair('l', "ㄌ"), '世' to Pair('s', "ㄕ"),
        '界' to Pair('j', "ㄐ"), '傳' to Pair('c', "ㄔ"), '奇' to Pair('q', "ㄑ"),
        '綠' to Pair('l', "ㄌ"), '紅' to Pair('h', "ㄏ"), '藍' to Pair('l', "ㄌ"),
        '綠' to Pair('l', "ㄌ"), '金' to Pair('j', "ㄐ"), '銀' to Pair('y', "ㄧ"),
        '水' to Pair('s', "ㄕ"), '晶' to Pair('j', "ㄐ"), '火' to Pair('h', "ㄏ"),
        '紅' to Pair('h', "ㄏ"), '葉' to Pair('y', "ㄧ"), '綠' to Pair('l', "ㄌ")
    )

    // 常用中文遊戲漢字全拼字典 (字 -> 全拼)
    private val CHAR_PINYIN_FULL_MAP = mapOf(
        '電' to "dian", '力' to "li", '超' to "chao", '級' to "ji", '级' to "ji",
        '瑪' to "ma", '玛' to "ma", '莉' to "li", '歐' to "ou", '欧' to "ou",
        '賽' to "sai", '赛' to "sai", '車' to "che", '车' to "che", '格' to "ge",
        '鬥' to "dou", '斗' to "dou", '冒' to "mao", '險' to "xian", '险' to "xian",
        '勇' to "yong", '者' to "zhe", '惡' to "e", '恶' to "e", '龍' to "long",
        '龙' to "long", '太' to "tai", '空' to "kong", '戰' to "zhan", '战' to "zhan",
        '士' to "shi", '寶' to "bao", '宝' to "bao", '可' to "ke", '夢' to "meng",
        '梦' to "meng", '精' to "jing", '靈' to "ling", '灵' to "ling", '神' to "shen",
        '奇' to "qi", '三' to "san", '國' to "guo", '国' to "guo", '志' to "zhi",
        '洛' to "luo", '克' to "ke", '人' to "ren", '魔' to "mo", '城' to "cheng",
        '魂' to "hun", '羅' to "luo", '罗' to "luo", '熱' to "re", '热' to "re",
        '血' to "xue", '快' to "kuai", '打' to "da", '星' to "xing", '之' to "zhi",
        '卡' to "ka", '比' to "bi", '薩' to "sa", '萨' to "sa", '爾' to "er",
        '达' to "da", '達' to "da", '傳' to "chuan", '传' to "chuan", '說' to "shuo",
        '说' to "shuo", '生' to "sheng", '化' to "hua", '危' to "wei", '機' to "ji",
        '机' to "ji", '合' to "he", '金' to "jin", '彈' to "dan", '弹' to "dan",
        '街' to "jie", '霸' to "ba", '拳' to "quan", '皇' to "huang", '終' to "zhong",
        '终' to "zhong", '幻' to "huan", '想' to "xiang", '恐' to "kong", '島' to "dao",
        '岛' to "dao", '忍' to "ren", '大' to "da", '亂' to "luan", '乱' to "luan",
        '版' to "ban", '修' to "xiu", '改' to "gai", '無' to "wu", '敵' to "di",
        '中' to "zhong", '文' to "wen", '漢' to "han", '化' to "hua"
    )

    /**
     * 繁簡標準化與字元小寫清洗
     */
    fun normalizeText(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFKC)
        val sb = StringBuilder()
        for (c in decomposed) {
            val mapped = TRAD_TO_SIMP[c] ?: c
            sb.append(mapped)
        }
        return sb.toString().trim().lowercase(Locale.ROOT)
    }

    /**
     * 生成真實的拼音全拼、首字母聲母與注音簡拼索引 (R2-10)
     */
    fun generateSearchIndex(title: String): Pair<String, String> {
        val clean = normalizeText(title)
        val initialBuilder = StringBuilder()
        val fullPinyinBuilder = StringBuilder()
        val zhuyinBuilder = StringBuilder()

        for (ch in clean) {
            if (ch in 'a'..'z' || ch in '0'..'9') {
                initialBuilder.append(ch)
                fullPinyinBuilder.append(ch)
                zhuyinBuilder.append(ch)
            } else if (isChinese(ch)) {
                val phonetic = CHAR_PHONETIC_MAP[ch]
                val fullPinyin = CHAR_PINYIN_FULL_MAP[ch]
                if (phonetic != null) {
                    initialBuilder.append(phonetic.first)
                    zhuyinBuilder.append(phonetic.second)
                }
                if (fullPinyin != null) {
                    fullPinyinBuilder.append(fullPinyin)
                }
            }
        }

        // 索引包含：首字母聲母 + 空格 + 全拼 (若有)
        val pinyinCombined = if (fullPinyinBuilder.isNotEmpty()) {
            "${initialBuilder} ${fullPinyinBuilder}"
        } else {
            initialBuilder.toString()
        }

        return Pair(pinyinCombined, zhuyinBuilder.toString())
    }

    /**
     * 統一查詢與標題繁簡正規化比對 (R2-10)
     */
    fun matches(title: String, pinyinIndex: String, zhuyinIndex: String, query: String): Boolean {
        if (query.isBlank()) return true
        val cleanQuery = normalizeText(query)
        val cleanTitle = normalizeText(title)

        // 1. 中文或英文標題子字串直配 (繁簡通用)
        if (cleanTitle.contains(cleanQuery)) return true

        // 2. 拼音首字母或全拼比對
        if (pinyinIndex.contains(cleanQuery)) return true

        // 3. 注音符號比對
        if (zhuyinIndex.contains(cleanQuery)) return true

        return false
    }

    private fun isChinese(c: Char): Boolean {
        val ub = Character.UnicodeBlock.of(c)
        return ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
               ub == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
               ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
    }
}
