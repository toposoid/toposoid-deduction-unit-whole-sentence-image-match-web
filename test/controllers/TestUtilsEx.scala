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

import com.ideal.linked.toposoid.common.{FeatureType, DataEntryType,  Neo4JUtilsImpl, ToposoidUtils, TransversalState}
import com.ideal.linked.toposoid.knowledgebase.regist.model.{ImageReference, Knowledge, KnowledgeForImage, PropositionRelation, Reference}
import com.ideal.linked.common.DeploymentConverter.conf
import com.ideal.linked.toposoid.knowledgebase.featurevector.model.{FeatureVectorIdentifier}
import com.ideal.linked.toposoid.knowledgebase.model.{KnowledgeBaseNode, KnowledgeBaseSemiGlobalNode, KnowledgeFeatureReference, LocalContext, LocalContextForFeature}
import com.ideal.linked.toposoid.protocol.model.base.{AnalyzedSentenceObject, AnalyzedSentenceObjects}
import com.ideal.linked.toposoid.protocol.model.neo4j.Neo4jRecords
import com.ideal.linked.toposoid.protocol.model.parser.{KnowledgeForParser, KnowledgeSentenceSetForParser}
import com.ideal.linked.toposoid.test.utils.TestUtils
import com.typesafe.scalalogging.LazyLogging
import play.api.libs.json.Json
import com.ideal.linked.toposoid.protocol.model.base.VerifyingEdges
import com.ideal.linked.toposoid.protocol.model.base.DeductionResult
import com.ideal.linked.toposoid.knowledgebase.image.model.RegisteredImageContentResult
import com.ideal.linked.toposoid.knowledgebase.regist.model.KnowledgeForTable
import com.ideal.linked.toposoid.protocol.model.parser.InputSentenceForParser
import com.ideal.linked.toposoid.knowledgebase.table.model.RegisteredTableContentResult
//import io.jvm.uuid.UUID


//case class ImageBoxInfo(x:Int, y:Int, weight:Int, height:Int)

object TestUtilsEx extends LazyLogging {
  val neo4JUtils = new Neo4JUtilsImpl()
  def deleteNeo4JAllData(transversalState: TransversalState): Unit = {
    val query = "MATCH (n) OPTIONAL MATCH (n)-[r]-() DELETE n,r"
    neo4JUtils.executeQuery(query, transversalState)
  }

  def executeQueryAndReturn(query: String, transversalState: TransversalState): Neo4jRecords = {
    neo4JUtils.executeQueryAndReturn(query, transversalState)
  }

  def registerSingleClaim(knowledgeForParser: KnowledgeForParser, transversalState: TransversalState): Unit = {
    val knowledgeSentenceSetForParser = KnowledgeSentenceSetForParser(
      List.empty[KnowledgeForParser],
      List.empty[PropositionRelation],
      List(knowledgeForParser),
      List.empty[PropositionRelation])
    TestUtils.registerData(knowledgeSentenceSetForParser, transversalState, addVectorFlag = true)
    Thread.sleep(5000)
  }


  var usedUuidList = List.empty[String]

  def getUUID(): String = {
    var uuid: String = java.util.UUID.randomUUID().toString
    while (usedUuidList.filter(_.equals(uuid)).size > 0) {
      uuid = java.util.UUID.randomUUID().toString
    }
    usedUuidList = usedUuidList :+ uuid
    uuid
  }
  /*
  def getAnalyzedSentenceObjectsJsonForSemiGlobal(lang:String,inputSentenceForParser: InputSentenceForParser, transversalState:TransversalState/*, knowledgeForImages:List[KnowledgeForImage]=List.empty[KnowledgeForImage], knowledgeForTables:List[KnowledgeForTable]=List.empty[KnowledgeForTable]*/): String = {
    
    val inputSentenceForParserJson = Json.toJson(inputSentenceForParser).toString
    val json = lang match {
      case "ja_JP" => ToposoidUtils.callComponent(inputSentenceForParserJson, conf.getString("TOPOSOID_SENTENCE_PARSER_JP_WEB_HOST"), conf.getString("TOPOSOID_SENTENCE_PARSER_JP_WEB_PORT"), "analyze", transversalState)
      case "en_US" => ToposoidUtils.callComponent(inputSentenceForParserJson, conf.getString("TOPOSOID_SENTENCE_PARSER_EN_WEB_HOST"), conf.getString("TOPOSOID_SENTENCE_PARSER_EN_WEB_PORT"), "analyze", transversalState)
    }

    val asos: AnalyzedSentenceObjects = Json.parse(json).as[AnalyzedSentenceObjects]
    val updatedAsos = asos.analyzedSentenceObjects.foldLeft(List.empty[AnalyzedSentenceObject]) {      
      (acc, x) => {

        val targetKnoledge = (inputSentenceForParser.premise ::: inputSentenceForParser.claim).filter(y => y.sentenceId.equals(x.knowledgeBaseSemiGlobalNode.sentenceId)).head

        
        val knowledgeFeatureReferenceImage: List[KnowledgeFeatureReference] = targetKnoledge.knowledge.knowledgeForImages.map(y => {          
          val json: String = Json.toJson(y).toString()
          val knowledgeForImageJson: String = ToposoidUtils.callComponent(json,
            conf.getString("TOPOSOID_CONTENTS_ADMIN_HOST"),
            conf.getString("TOPOSOID_CONTENTS_ADMIN_PORT"),
            "convertImage", transversalState)
          val registeredContentResult: RegisteredImageContentResult = Json.parse(knowledgeForImageJson).as[RegisteredImageContentResult]
          if (registeredContentResult.statusInfo.status.equals("ERROR")) throw new Exception(registeredContentResult.statusInfo.message)          
          KnowledgeFeatureReference(
            propositionId = x.knowledgeBaseSemiGlobalNode.propositionId,
            sentenceId = x.knowledgeBaseSemiGlobalNode.sentenceId,
            featureId = registeredContentResult.knowledgeForImage.id,
            featureType = FeatureType.IMAGE.index,
            url = registeredContentResult.knowledgeForImage.imageReference.reference.url,
            source = registeredContentResult.knowledgeForImage.imageReference.reference.originalUrlOrReference,
            featureInputType = DataEntryType.MANUAL.index)        
        })

        val knowledgeFeatureReferenceTable: List[KnowledgeFeatureReference] = targetKnoledge.knowledge.knowledgeForTables.map(y => {          
          val json: String = Json.toJson(KnowledgeForTable(y.id, y.tableReference)).toString()
          val knowledgeForTableJson: String = ToposoidUtils.callComponent(json,
            conf.getString("TOPOSOID_CONTENTS_ADMIN_HOST"),
            conf.getString("TOPOSOID_CONTENTS_ADMIN_PORT"),
            "convertTable", transversalState)
          val registeredContentResult: RegisteredTableContentResult = Json.parse(knowledgeForTableJson).as[RegisteredTableContentResult]
          if (registeredContentResult.statusInfo.status.equals("ERROR")) throw new Exception(registeredContentResult.statusInfo.message)
          KnowledgeFeatureReference(
            propositionId = x.knowledgeBaseSemiGlobalNode.propositionId,
            sentenceId = x.knowledgeBaseSemiGlobalNode.sentenceId,
            featureId = registeredContentResult.knowledgeForTable.id,
            featureType = FeatureType.IMAGE.index,
            url = registeredContentResult.knowledgeForTable.tableReference.reference.url,
            source = registeredContentResult.knowledgeForTable.tableReference.reference.originalUrlOrReference,
            featureInputType = DataEntryType.MANUAL.index)                   
        })


        val localContextForFeature = LocalContextForFeature(
          x.knowledgeBaseSemiGlobalNode.localContextForFeature.lang,knowledgeFeatureReferenceImage:::knowledgeFeatureReferenceTable)

        val knowledgeBaseSemiGlobalNode = KnowledgeBaseSemiGlobalNode(
          sentenceId = x.knowledgeBaseSemiGlobalNode.sentenceId,
          propositionId = x.knowledgeBaseSemiGlobalNode.propositionId,
          documentId = x.knowledgeBaseSemiGlobalNode.documentId,
          sentence = x.knowledgeBaseSemiGlobalNode.sentence,
          sentenceType = x.knowledgeBaseSemiGlobalNode.sentenceType,
          localContextForFeature = localContextForFeature)

        acc :+ AnalyzedSentenceObject(
          nodeMap = x.nodeMap,
          edgeList = x.edgeList,
          knowledgeBaseSemiGlobalNode = knowledgeBaseSemiGlobalNode,
          deductionResult = x.deductionResult)
      }
    }
    Json.toJson(AnalyzedSentenceObjects(updatedAsos, asos.deductionConfiguration)).toString()
  }
  */
}
