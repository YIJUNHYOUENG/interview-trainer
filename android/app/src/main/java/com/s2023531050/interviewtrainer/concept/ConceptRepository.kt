package com.s2023531050.interviewtrainer.concept

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** 4지선다 퀴즈 한 문제. [answer] 는 [options] 안의 정답 위치(0~3). */
data class Quiz(
    val question: String,
    val options: List<String>,
    val answer: Int,
    val explanation: String,
)

data class InterviewQA(val question: String, val answer: String)

/** 개념 하나. [id] 는 "os-1" 처럼 카테고리 id 와 순서로 정해지고 학습 진도(Room)의 키로 쓰인다. */
data class Concept(
    val id: String,
    val categoryId: String,
    val categoryName: String,
    val title: String,
    val summary: String,
    val keywords: List<String>,
    val explanation: String,
    val interviewQA: List<InterviewQA>,
    val quizzes: List<Quiz>,
)

data class Category(val id: String, val name: String, val concepts: List<Concept>)

/**
 * assets/concepts.json (docs/DESIGN.md 6장)을 읽어 둔다. 학습 내용은 바뀌지 않으므로 한 번만 읽어 메모리에 둔다.
 * 파일은 서버의 `tools/gen_concepts.py` 가 만든다.
 */
class ConceptData(val version: Int, val categories: List<Category>) {
    val all: List<Concept> = categories.flatMap { it.concepts }
    private val byId = all.associateBy { it.id }

    fun get(id: String): Concept? = byId[id]

    /** 같은 카테고리 안에서 [concept] 의 앞(offset=-1)/뒤(offset=1) 개념. 없으면 null. */
    fun neighbor(concept: Concept, offset: Int): Concept? {
        val list = categories.firstOrNull { it.id == concept.categoryId }?.concepts ?: return null
        return list.getOrNull(list.indexOfFirst { it.id == concept.id } + offset)
    }
}

object ConceptRepository {
    @Volatile private var cache: ConceptData? = null

    suspend fun load(context: Context): ConceptData {
        cache?.let { return it }
        return withContext(Dispatchers.IO) {
            val text = context.assets.open("concepts.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            parse(JSONObject(text)).also { cache = it }
        }
    }

    private fun parse(root: JSONObject): ConceptData {
        val categories = root.getJSONArray("categories").objects().map { c ->
            val catId = c.getString("id")
            val catName = c.getString("name")
            Category(
                catId, catName,
                c.getJSONArray("concepts").objects().map { x ->
                    Concept(
                        id = x.getString("id"),
                        categoryId = catId,
                        categoryName = catName,
                        title = x.getString("title"),
                        summary = x.getString("summary"),
                        keywords = x.getJSONArray("keywords").strings(),
                        explanation = x.getString("explanation"),
                        interviewQA = x.getJSONArray("interviewQA").objects()
                            .map { InterviewQA(it.getString("question"), it.getString("answer")) },
                        quizzes = x.getJSONArray("quizzes").objects().map { q ->
                            Quiz(
                                q.getString("question"), q.getJSONArray("options").strings(),
                                q.getInt("answer"), q.getString("explanation"),
                            )
                        },
                    )
                },
            )
        }
        return ConceptData(root.optInt("version", 1), categories)
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
}
