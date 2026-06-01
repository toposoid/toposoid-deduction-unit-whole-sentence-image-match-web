/*
 * Copyright (C) 2025  Linked Ideal LLC.[https://linked-ideal.com/]
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package controllers

import com.ideal.linked.common.DeploymentConverter.conf
import com.ideal.linked.toposoid.common.{SentenceType, FeatureType, ScopeType, TRANSVERSAL_STATE, ToposoidUtils, TransversalState}
//import com.ideal.linked.toposoid.deduction.common.FacadeForAccessNeo4J.getCypherQueryResult
//import com.ideal.linked.toposoid.deduction.common.{DeductionUnitControllerForSemiGlobal, FeatureVectorSearchInfo}
import com.ideal.linked.toposoid.knowledgebase.featurevector.model.{FeatureVectorIdentifier, FeatureVectorSearchResult, SingleFeatureVectorForSearch}
import com.ideal.linked.toposoid.protocol.model.base.{AnalyzedSentenceObject, AnalyzedSentenceObjects, KnowledgeBaseSideInfo, MatchedFeatureInfo}
import com.ideal.linked.toposoid.protocol.model.neo4j.Neo4jRecords
import com.ideal.linked.toposoid.vectorizer.FeatureVectorizer
import com.typesafe.scalalogging.LazyLogging

import javax.inject._
import play.api._
import play.api.mvc._
import play.api.libs.json.{Json, __}
import play.api.libs.json.JsValue
import com.ideal.linked.toposoid.protocol.model.base.VerifyingEdges
import com.ideal.linked.toposoid.protocol.model.base.CoveredPropositionEdge
import com.ideal.linked.toposoid.common.DeductionUtilsForSemiGlobal
import com.ideal.linked.toposoid.protocol.model.base.MatchedKnowledgeNode
import com.ideal.linked.toposoid.knowledgebase.model.KnowledgeBaseNode
import com.ideal.linked.toposoid.protocol.model.base.CoveredPropositionNode
import com.ideal.linked.toposoid.knowledgebase.featurevector.model.StatusInfo
import com.ideal.linked.toposoid.knowledgebase.regist.model.Knowledge

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(val controllerComponents: ControllerComponents) extends BaseController /*with DeductionUnitControllerForSemiGlobal*/ with LazyLogging {
  /**
   *
   * @return
   */
  def execute():Action[JsValue] = Action(parse.json[JsValue])  { request =>
    val transversalState = Json.parse(request.headers.get(TRANSVERSAL_STATE .str).get).as[TransversalState]
    try {
      val json = request.body
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(json.toString).as[AnalyzedSentenceObjects]
      val asos: List[AnalyzedSentenceObject] = analyzedSentenceObjects.analyzedSentenceObjects
      /*
      val result: List[AnalyzedSentenceObject] = asos.foldLeft(List.empty[AnalyzedSentenceObject]) {
        (acc, x) => acc :+ analyze(x, acc, "whole-sentence-image-feature-match", List(FeatureType.IMAGE.index), transversalState)
      }
      //Check if the image exists on asos here　or not.
      logger.info(ToposoidUtils.formatMessageForLogger("deduction completed.", transversalState.userId))
      Ok(Json.toJson(AnalyzedSentenceObjects(result))).as(JSON)
      */
      val result:List[VerifyingEdges] = asos.foldLeft(List.empty[VerifyingEdges]){
        (acc, aso) => {    
          acc :+ VerifyingEdges(            
            propositionId = aso.knowledgeBaseSemiGlobalNode.propositionId,
            sentenceId = aso.knowledgeBaseSemiGlobalNode.sentenceId,
            coveredPropositionEdges = analyzeGraphKnowledgeForSemiGlobal(aso, transversalState)
          )
        }
      }
      logger.info(ToposoidUtils.formatMessageForLogger("Embedded Image In Whole Sentence analysis completed.", transversalState.userId))      
      Ok(Json.toJson(result)).as(JSON)        

    } catch {
      case e: Exception => {
        logger.error(ToposoidUtils.formatMessageForLogger(e.toString, transversalState.userId), e)
        BadRequest(Json.obj("status" -> "Error", "message" -> e.toString()))
      }
    }
  }

  /**
   *
   * @param aso
   * @return
   */
  def analyzeGraphKnowledgeForSemiGlobal(aso: AnalyzedSentenceObject, transversalState:TransversalState): List[CoveredPropositionEdge] = {

    aso.knowledgeBaseSemiGlobalNode.localContextForFeature.knowledgeFeatureReferences.foldLeft(List.empty[CoveredPropositionEdge]) {
      (acc, x) => {
        val featureVectorSearchResult = getFeatureVectorSearchResult(FeatureType.IMAGE, aso.knowledgeBaseSemiGlobalNode.sentenceType, "", "",  x.url, transversalState)
        featureVectorSearchResult.ids.size match {
          case 0 => acc
          case _ => acc  ::: getCoveredPropositionEdges(aso, featureVectorSearchResult, transversalState)
        }
        /*
        val imageFeatures: List[CoveredPropositionEdge] = getMatchedImageFeature(
          aso.knowledgeBaseSemiGlobalNode.sentenceType,
          x.url,
          transversalState
        )
        imageFeatures.size match {
          case 0 => acc
          case _ => acc ::: imageFeatures
        }
        */
      }
    }
  }

  //TODO toposoid-feature-vectorizer へ移動 featureTypeを持たせる。
  private def getFeatureVectorSearchResult(featureType:FeatureType,  originalSentenceType: Int, sentence:String, lang:String, url:String, transversalState:TransversalState): FeatureVectorSearchResult = {

    val featureVectorSearchResultJson = featureType match {
      case FeatureType.SENTENCE => {
        val vector = FeatureVectorizer.getSentenceVector(Knowledge(sentence, lang, "{}"), transversalState)
        val json: String = Json.toJson(SingleFeatureVectorForSearch(vector = vector.vector, num = conf.getString("TOPOSOID_SENTENCE_VECTORDB_SEARCH_NUM_MAX").toInt)).toString()
        ToposoidUtils.callComponent(json, conf.getString("TOPOSOID_SENTENCE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_SENTENCE_VECTORDB_ACCESSOR_PORT"), "search", transversalState)
      }
      case FeatureType.IMAGE => {
        val vector = FeatureVectorizer.getImageVector(url, transversalState)
        val json: String = Json.toJson(SingleFeatureVectorForSearch(vector = vector.vector, num = conf.getString("TOPOSOID_IMAGE_VECTORDB_SEARCH_NUM_MAX").toInt)).toString()
        ToposoidUtils.callComponent(json, conf.getString("TOPOSOID_IMAGE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_IMAGE_VECTORDB_ACCESSOR_PORT"), "search", transversalState)
      }
      case FeatureType.TABLE => {
        //TODO:Implement
        Json.toJson(FeatureVectorSearchResult(List.empty[FeatureVectorIdentifier], List.empty[Float], StatusInfo("Ok", ""))).toString
      } 
      case _ => {
        Json.toJson(FeatureVectorSearchResult(List.empty[FeatureVectorIdentifier], List.empty[Float], StatusInfo("Ok", ""))).toString
      }
    }

    Json.parse(featureVectorSearchResultJson).as[FeatureVectorSearchResult]

  }
  
  private def getCoveredPropositionEdges(aso:AnalyzedSentenceObject ,featureVectorSearchResult: FeatureVectorSearchResult, transversalState:TransversalState): List[CoveredPropositionEdge] = {

    val (ids, similarities) = (featureVectorSearchResult.ids zip featureVectorSearchResult.similarities).foldLeft((List.empty[FeatureVectorIdentifier], List.empty[Float])) {
      (acc, x) => {
        x._1.sentenceType match {
          case SentenceType.CLAIM.index => (acc._1 :+ x._1, acc._2 :+ x._2)
          case _ => acc
        }
      }
    }

    val filteredResult = FeatureVectorSearchResult(ids, similarities, featureVectorSearchResult.statusInfo) 
    val deductionUnitName = conf.getString("TOPOSOID_DEDUCTION_UNIT_NAME")
    filteredResult.ids.size match {
      case 0 => List.empty[CoveredPropositionEdge]
      case _ => {        
        val featureVectorSearchInfoList = DeductionUtilsForSemiGlobal.extractExistInNeo4JResultForSentence(filteredResult, aso.knowledgeBaseSemiGlobalNode.sentenceType, transversalState)        
        val matchedKnowledgeNodes = featureVectorSearchInfoList.map(x => {
          MatchedKnowledgeNode(
            propositionId = x.propositionId,
            sentenceId = x.sentenceId,
            nodeId = "",
            caseNameOnEdge = "",
            isDenialWord = false,
            nodeType = x.sentenceType,
            featureInfo = MatchedFeatureInfo(featureId = x.featureId, similarity = x.similarity)
          )          
        })

        aso.edgeList.map(x => {
          val sourceNode = aso.nodeMap.get(x.sourceId).get.asInstanceOf[KnowledgeBaseNode]
          val destinationNode = aso.nodeMap.get(x.destinationId).get.asInstanceOf[KnowledgeBaseNode]
          val sourceCoveredPropositionNode = CoveredPropositionNode(
            terminalId = sourceNode.nodeId,
            terminalSurface = sourceNode.predicateArgumentStructure.surface,
            terminalUrl = "",
            matchedKnowledgeNodes = matchedKnowledgeNodes,
            isConfirmed = true,
            deductionUnit = deductionUnitName
          )

          val destinationCoveredPropositionNode = CoveredPropositionNode(
            terminalId = destinationNode.nodeId,
            terminalSurface = destinationNode.predicateArgumentStructure.surface,
            terminalUrl = "",
            matchedKnowledgeNodes = matchedKnowledgeNodes,
            isConfirmed = true,
            deductionUnit = deductionUnitName
          )
          CoveredPropositionEdge(sourceCoveredPropositionNode, destinationCoveredPropositionNode)
        }) 
      }
    }    
      
    List.empty[CoveredPropositionEdge]
  }


  /**
   *
   * @param originalSentenceType
   * @param url
   * @return
   */
  /*
  private def getMatchedImageFeature(originalSentenceType: Int, url:String, transversalState:TransversalState): List[CoveredPropositionEdge] = {
    /*
    val vector = FeatureVectorizer.getImageVector(url, transversalState)
    val json: String = Json.toJson(SingleFeatureVectorForSearch(vector = vector.vector, num = conf.getString("TOPOSOID_IMAGE_VECTORDB_SEARCH_NUM_MAX").toInt)).toString()
    val featureVectorSearchResultJson: String = ToposoidUtils.callComponent(json, conf.getString("TOPOSOID_IMAGE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_IMAGE_VECTORDB_ACCESSOR_PORT"), "search", transversalState)
    val result = Json.parse(featureVectorSearchResultJson).as[FeatureVectorSearchResult]
    */

    //TODO:Sentenceも一致しているかどうかチェックするか？　環境変数で設定できると良いのかも
    //VecotrDBにClaimとして存在している場合に推論が可能になる
    val (ids, similarities) = (result.ids zip result.similarities).foldLeft((List.empty[FeatureVectorIdentifier], List.empty[Float])) {
      (acc, x) => {
        x._1.sentenceType match {
          case SentenceType.CLAIM.index => (acc._1 :+ x._1, acc._2 :+ x._2)
          case _ => acc
        }
      }
    }

    val filteredResult = FeatureVectorSearchResult(ids, similarities, result.statusInfo) 
    val deductionUnitName = conf.getString("TOPOSOID_DEDUCTION_UNIT_NAME")
    filteredResult.ids.size match {
      case 0 => List.empty[CoveredPropositionEdge]
      case _ => {        
        val featureVectorSearchInfoList = DeductionUtilsForSemiGlobal.extractExistInNeo4JResultForSentence(filteredResult, originalSentenceType, transversalState)        
        val matchedKnowledgeNodes = featureVectorSearchInfoList.map(x => {
          MatchedKnowledgeNode(
            propositionId = x.propositionId,
            sentenceId = x.sentenceId,
            nodeId = "",
            caseNameOnEdge = "",
            isDenialWord = false,
            nodeType = x.sentenceType,
            featureInfo = MatchedFeatureInfo(featureId = x.featureId, similarity = x.similarity)
          )          
        })

        aso.edgeList.map(x => {
          val sourceNode = aso.nodeMap.get(x.sourceId).get.asInstanceOf[KnowledgeBaseNode]
          val destinationNode = aso.nodeMap.get(x.destinationId).get.asInstanceOf[KnowledgeBaseNode]
          val sourceCoveredPropositionNode = CoveredPropositionNode(
            terminalId = sourceNode.nodeId,
            terminalSurface = sourceNode.predicateArgumentStructure.surface,
            terminalUrl = "",
            matchedKnowledgeNodes = matchedKnowledgeNodes,
            isConfirmed = true,
            deductionUnit = deductionUnitName
          )

          val destinationCoveredPropositionNode = CoveredPropositionNode(
            terminalId = destinationNode.nodeId,
            terminalSurface = destinationNode.predicateArgumentStructure.surface,
            terminalUrl = "",
            matchedKnowledgeNodes = matchedKnowledgeNodes,
            isConfirmed = true,
            deductionUnit = deductionUnitName
          )
          CoveredPropositionEdge(sourceCoveredPropositionNode, destinationCoveredPropositionNode)
        }) 
      }
    }    
        
  

  }
  */
  /**
   *
   * @param featureVectorSearchResult
   * @param originalSentenceType
   * @return
   */
  /*
  private def extractExistInNeo4JResultForImage(featureVectorSearchResult: FeatureVectorSearchResult, originalSentenceType: Int, transversalState:TransversalState): List[FeatureVectorSearchInfo] = {

    (featureVectorSearchResult.ids zip featureVectorSearchResult.similarities).foldLeft(List.empty[FeatureVectorSearchInfo]) {
      (acc, x) => {
        val idInfo = x._1
        val propositionId = idInfo.superiorId
        val lang = idInfo.lang
        val featureId = idInfo.featureId
        val similarity = x._2
        val nodeType: String = ToposoidUtils.getNodeType(idInfo.sentenceType, ScopeType.SEMIGLOBAL.index, FeatureType.IMAGE.index)
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
  */
}
