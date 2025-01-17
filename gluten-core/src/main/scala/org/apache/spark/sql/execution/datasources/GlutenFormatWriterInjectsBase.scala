/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.spark.sql.execution.datasources

import io.glutenproject.execution.TransformSupport
import io.glutenproject.execution.WholeStageTransformer
import io.glutenproject.execution.datasource.GlutenFormatWriterInjects
import io.glutenproject.extension.TransformPreOverrides
import io.glutenproject.extension.columnar.AddTransformHintRule

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.execution.ColumnarCollapseTransformStages.transformStageCounter
import org.apache.spark.sql.execution.SparkPlan

trait GlutenFormatWriterInjectsBase extends GlutenFormatWriterInjects {

  /**
   * FileFormatWriter wraps some Project & Sort on the top of the original output spark plan, we
   * need to replace them with Columnar version
   * @param plan
   *   must be a FakeRowAdaptor
   * @return
   */
  override def executeWriterWrappedSparkPlan(plan: SparkPlan): RDD[InternalRow] = {
    if (plan.isInstanceOf[FakeRowAdaptor]) {
      // here, the FakeRowAdaptor is simply a R2C converter
      return plan.execute()
    }

    val transformed = TransformPreOverrides(false).apply(AddTransformHintRule().apply(plan))
    if (!transformed.isInstanceOf[TransformSupport]) {
      throw new IllegalStateException(
        "Cannot transform the SparkPlans wrapped by FileFormatWriter, " +
          "consider disabling native writer to workaround this issue.")
    }
    val wst = WholeStageTransformer(transformed)(transformStageCounter.incrementAndGet())
    FakeRowAdaptor(wst)
      .execute()
  }
}
