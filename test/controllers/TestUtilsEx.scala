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

import com.ideal.linked.toposoid.common.{CLAIM, IMAGE, MANUAL, Neo4JUtilsImpl, PREMISE, SENTENCE, ToposoidUtils, TransversalState}
import com.ideal.linked.toposoid.knowledgebase.regist.model.{ImageReference, Knowledge, KnowledgeForImage, PropositionRelation, Reference}
import com.ideal.linked.common.DeploymentConverter.conf
import com.ideal.linked.toposoid.knowledgebase.featurevector.model.{FeatureVectorIdentifier, RegistContentResult}
import com.ideal.linked.toposoid.knowledgebase.model.{KnowledgeBaseNode, KnowledgeBaseSemiGlobalNode, KnowledgeFeatureReference, LocalContext, LocalContextForFeature}
import com.ideal.linked.toposoid.protocol.model.base.{AnalyzedSentenceObject, AnalyzedSentenceObjects}
import com.ideal.linked.toposoid.protocol.model.neo4j.Neo4jRecords
import com.ideal.linked.toposoid.protocol.model.parser.{KnowledgeForParser, KnowledgeSentenceSetForParser}
import com.ideal.linked.toposoid.test.utils.TestUtils
import com.typesafe.scalalogging.LazyLogging
import play.api.libs.json.Json
import io.jvm.uuid.UUID


case class ImageBoxInfo(x:Int, y:Int, weight:Int, height:Int)

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
    var uuid: String = UUID.random.toString
    while (usedUuidList.filter(_.equals(uuid)).size > 0) {
      uuid = UUID.random.toString
    }
    usedUuidList = usedUuidList :+ uuid
    uuid
  }


  def getKnowledge(lang:String, sentence: String, reference: Reference, imageBoxInfo: ImageBoxInfo, transversalState:TransversalState): Knowledge = {
    Knowledge(sentence, lang, "{}", false, List(getImageInfo(reference, imageBoxInfo, transversalState)))
  }

  def getImageInfo(reference: Reference, imageBoxInfo: ImageBoxInfo, transversalState:TransversalState): KnowledgeForImage = {
    val imageReference = ImageReference(reference: Reference, imageBoxInfo.x, imageBoxInfo.y, imageBoxInfo.weight, imageBoxInfo.height)
    val knowledgeForImage = KnowledgeForImage(id = getUUID(), imageReference = imageReference)
    val registContentResultJson = ToposoidUtils.callComponent(
      Json.toJson(knowledgeForImage).toString(),
      conf.getString("TOPOSOID_CONTENTS_ADMIN_HOST"),
      conf.getString("TOPOSOID_CONTENTS_ADMIN_PORT"),
      "registImage",
      transversalState)
    val registContentResult: RegistContentResult = Json.parse(registContentResultJson).as[RegistContentResult]
    registContentResult.knowledgeForImage
  }
  /*
  def deleteFeatureVector(propositionId:String, sentenceId:String, sentenceType:Int, knowledge: Knowledge, transversalState:TransversalState)={

    val featureVectorIdentifier = FeatureVectorIdentifier(propositionId = propositionId, featureId = sentenceId, sentenceType = sentenceType, lang = knowledge.lang)
    val json = Json.toJson(featureVectorIdentifier).toString()
    ToposoidUtils.callComponent(json, conf.getString("TOPOSOID_SENTENCE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_SENTENCE_VECTORDB_ACCESSOR_PORT"), "delete", transversalState)

    knowledge.knowledgeForImages.map(x => {
      val featureVectorIdentifier = FeatureVectorIdentifier(propositionId = propositionId, featureId = x.id, sentenceType = sentenceType, lang = knowledge.lang)
      val json2 = Json.toJson(featureVectorIdentifier).toString()
      ToposoidUtils.callComponent(json2, conf.getString("TOPOSOID_IMAGE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_IMAGE_VECTORDB_ACCESSOR_PORT"), "delete", transversalState)
    })
    Thread.sleep(5000)

  }
  */
  def addImageInfoToAnalyzedSentenceObjects(lang:String,inputSentence: String, knowledgeForImages: List[KnowledgeForImage], transversalState:TransversalState): String = {
    /**
     * CAUTION This function does not support cases where one node has multiple images!!!
     */
    val json = lang match {
      case "ja_JP" => ToposoidUtils.callComponent(inputSentence, conf.getString("TOPOSOID_SENTENCE_PARSER_JP_WEB_HOST"), conf.getString("TOPOSOID_SENTENCE_PARSER_JP_WEB_PORT"), "analyze", transversalState)
      case "en_US" => ToposoidUtils.callComponent(inputSentence, conf.getString("TOPOSOID_SENTENCE_PARSER_EN_WEB_HOST"), conf.getString("TOPOSOID_SENTENCE_PARSER_EN_WEB_PORT"), "analyze", transversalState)
    }

    val asos: AnalyzedSentenceObjects = Json.parse(json).as[AnalyzedSentenceObjects]
    val updatedAsos = asos.analyzedSentenceObjects.foldLeft(List.empty[AnalyzedSentenceObject]) {
      (acc, x) => {

        val knowledgeForImage = knowledgeForImages(acc.size)

        val knowledgeFeatureReference = KnowledgeFeatureReference(
          propositionId = x.knowledgeBaseSemiGlobalNode.propositionId,
          sentenceId = x.knowledgeBaseSemiGlobalNode.sentenceId,
          featureId = getUUID(),
          featureType = IMAGE.index,
          url = knowledgeForImage.imageReference.reference.url,
          source = knowledgeForImage.imageReference.reference.originalUrlOrReference,
          featureInputType = MANUAL.index,
          extentText = "{}")

        val localContextForFeature = LocalContextForFeature(
          x.knowledgeBaseSemiGlobalNode.localContextForFeature.lang,
          List(knowledgeFeatureReference))

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
    Json.toJson(AnalyzedSentenceObjects(updatedAsos)).toString()
  }
}
