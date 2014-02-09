/*
 *    Copyright (c) 2013, University of Toronto.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may
 *    not use this file except in compliance with the License. You may obtain
 *    a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 */
package edu.toronto.cs.xcurator.discoverer;

import edu.toronto.cs.xcurator.mapping.Mapping;
import org.w3c.dom.Document;

/**
 *
 * @author zhuerkan
 */
public interface MappingDiscoveryStep {
  
  /**
   * Processes the mapping.
   *
   * @param dataDoc The XML source document.
   * @param mapping The map of processed entities. Note that the step should only
   *    modify the mapping.
   */
  void process(Document dataDoc, Mapping mapping);
  
}
