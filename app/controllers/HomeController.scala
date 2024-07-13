/*
 * Copyright 2021 Linked Ideal LLC.[https://linked-ideal.com/]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers

import com.ideal.linked.common.DeploymentConverter.conf
import com.ideal.linked.toposoid.common.{CLAIM, IMAGE, SEMIGLOBAL, TRANSVERSAL_STATE, ToposoidUtils, TransversalState}
import com.ideal.linked.toposoid.deduction.common.FacadeForAccessNeo4J.getCypherQueryResult
import com.ideal.linked.toposoid.deduction.common.{DeductionUnitControllerForSemiGlobal, FeatureVectorSearchInfo}
import com.ideal.linked.toposoid.knowledgebase.featurevector.model.{FeatureVectorIdentifier, FeatureVectorSearchResult, SingleFeatureVectorForSearch}
import com.ideal.linked.toposoid.protocol.model.base.{AnalyzedSentenceObject, AnalyzedSentenceObjects, KnowledgeBaseSideInfo, MatchedFeatureInfo}
import com.ideal.linked.toposoid.protocol.model.neo4j.Neo4jRecords
import com.ideal.linked.toposoid.vectorizer.FeatureVectorizer
import com.typesafe.scalalogging.LazyLogging

import javax.inject._
import play.api._
import play.api.mvc._
import play.api.libs.json.{Json, __}

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(val controllerComponents: ControllerComponents) extends BaseController with DeductionUnitControllerForSemiGlobal with LazyLogging {
  /**
   *
   * @return
   */
  def execute() = Action(parse.json) { request =>
    val transversalState = Json.parse(request.headers.get(TRANSVERSAL_STATE .str).get).as[TransversalState]
    try {
      val json = request.body
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(json.toString).as[AnalyzedSentenceObjects]
      val asos: List[AnalyzedSentenceObject] = analyzedSentenceObjects.analyzedSentenceObjects

      val result: List[AnalyzedSentenceObject] = asos.foldLeft(List.empty[AnalyzedSentenceObject]) {
        (acc, x) => acc :+ analyze(x, acc, "whole-sentence-image-feature-match", List(IMAGE.index), transversalState)
      }
      //Check if the image exists on asos here　or not.
      logger.info(ToposoidUtils.formatMessageForLogger("deduction completed.", transversalState.username))
      Ok(Json.toJson(AnalyzedSentenceObjects(result))).as(JSON)

    } catch {
      case e: Exception => {
        logger.error(ToposoidUtils.formatMessageForLogger(e.toString, transversalState.username), e)
        BadRequest(Json.obj("status" -> "Error", "message" -> e.toString()))
      }
    }
  }

  /**
   *
   * @param aso
   * @return
   */
  def analyzeGraphKnowledgeForSemiGlobal(aso: AnalyzedSentenceObject, transversalState:TransversalState): List[KnowledgeBaseSideInfo] = {

    aso.knowledgeBaseSemiGlobalNode.localContextForFeature.knowledgeFeatureReferences.foldLeft(List.empty[KnowledgeBaseSideInfo]) {
      (acc, x) => {
        val imageFeatures: List[KnowledgeBaseSideInfo] = getMatchedImageFeature(
          aso.knowledgeBaseSemiGlobalNode.sentenceType,
          x.url,
          transversalState
        )
        imageFeatures.size match {
          case 0 => acc
          case _ => acc ::: imageFeatures
        }
      }
    }
  }

  /**
   *
   * @param originalSentenceType
   * @param url
   * @return
   */
  private def getMatchedImageFeature(originalSentenceType: Int, url:String, transversalState:TransversalState): List[KnowledgeBaseSideInfo] = {

    val vector = FeatureVectorizer.getImageVector(url, transversalState)
    val json: String = Json.toJson(SingleFeatureVectorForSearch(vector = vector.vector, num = conf.getString("TOPOSOID_IMAGE_VECTORDB_SEARCH_NUM_MAX").toInt)).toString()
    val featureVectorSearchResultJson: String = ToposoidUtils.callComponent(json, conf.getString("TOPOSOID_IMAGE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_IMAGE_VECTORDB_ACCESSOR_PORT"), "search", transversalState)
    val result = Json.parse(featureVectorSearchResultJson).as[FeatureVectorSearchResult]

    //TODO:Sentenceも一致しているかどうかチェックするか？　環境変数で設定できると良いのかも
    //VecotrDBにClaimとして存在している場合に推論が可能になる
    val (ids, similarities) = (result.ids zip result.similarities).foldLeft((List.empty[FeatureVectorIdentifier], List.empty[Float])) {
      (acc, x) => {
        x._1.sentenceType match {
          case CLAIM.index => (acc._1 :+ x._1, acc._2 :+ x._2)
          case _ => acc
        }
      }
    }

    val filteredResult = FeatureVectorSearchResult(ids, similarities, result.statusInfo)
    filteredResult.ids.size match {
      case 0 => List.empty[KnowledgeBaseSideInfo]
      case _ => {
        //sentenceごとに最も類似度が高いものを抽出する
        val featureVectorSearchInfoList = extractExistInNeo4JResultForImage(filteredResult, originalSentenceType, transversalState)
        featureVectorSearchInfoList.map(x => {
          KnowledgeBaseSideInfo(x.propositionId, x.sentenceId, List(MatchedFeatureInfo(featureId = x.featureId, similarity = x.similarity)))
        })
      }
    }

  }

  /**
   *
   * @param featureVectorSearchResult
   * @param originalSentenceType
   * @return
   */
  private def extractExistInNeo4JResultForImage(featureVectorSearchResult: FeatureVectorSearchResult, originalSentenceType: Int, transversalState:TransversalState): List[FeatureVectorSearchInfo] = {

    (featureVectorSearchResult.ids zip featureVectorSearchResult.similarities).foldLeft(List.empty[FeatureVectorSearchInfo]) {
      (acc, x) => {
        val idInfo = x._1
        val propositionId = idInfo.propositionId
        val lang = idInfo.lang
        val featureId = idInfo.featureId
        val similarity = x._2
        val nodeType: String = ToposoidUtils.getNodeType(idInfo.sentenceType, SEMIGLOBAL.index, IMAGE.index)
        //Check whether featureVectorSearchResult information exists in Neo4J
        val query = "MATCH (n:%s) WHERE n.propositionId='%s' AND n.featureId='%s' RETURN n".format(nodeType, propositionId, featureId)
        val jsonStr: String = getCypherQueryResult(query, "", transversalState)
        val neo4jRecords: Neo4jRecords = Json.parse(jsonStr).as[Neo4jRecords]
        neo4jRecords.records.size match {
          case 0 => acc
          case _ => {
            val idInfoOnNeo4jSide = neo4jRecords.records.head.head.value.featureNode.get
            //sentenceType returns the originalSentenceType of the argument
            acc :+ FeatureVectorSearchInfo(idInfoOnNeo4jSide.propositionId, idInfoOnNeo4jSide.sentenceId, originalSentenceType, lang, featureId, similarity)
          }
        }
      }
    }
  }

}
