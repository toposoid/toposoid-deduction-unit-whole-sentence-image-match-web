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
        val featureVectorSearchResult = FeatureVectorizer.getFeatureVectorSearchResult(FeatureType.IMAGE,  "", "",  x.url, transversalState)        
        val sentenceIds = aso.deductionResult.coveredPropositionEdges.foldLeft(List.empty[String]){
          (acc, x) =>
            acc ++ x.sourceNode.matchedKnowledgeNodes.map(y => "'" + y.sentenceId + "'")
        }.distinct

        //TODO: sentenceIdsに限定して、featureVectorSearchResultが存在するかを確認する。

        featureVectorSearchResult.ids.size match {
          case 0 => acc
          case _ => {
            acc  ::: DeductionUtilsForSemiGlobal.getCoveredPropositionEdges(true, aso, featureVectorSearchResult, transversalState)
          }
        }
      }
    }
  }

}
