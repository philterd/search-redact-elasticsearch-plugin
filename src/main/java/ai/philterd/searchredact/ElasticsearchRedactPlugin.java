/*
 *     Copyright 2025 Philterd, LLC @ https://www.philterd.ai
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.philterd.searchredact;

import ai.philterd.phileas.PhileasConfiguration;
import ai.philterd.phileas.services.context.DefaultContextService;
import ai.philterd.phileas.services.disambiguation.vector.InMemoryVectorService;
import ai.philterd.phileas.services.filters.filtering.PlainTextFilterService;
import ai.philterd.searchredact.ext.SearchRedactParametersExtBuilder;
import org.elasticsearch.action.support.ActionFilter;
import org.elasticsearch.plugins.ActionPlugin;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.plugins.SearchPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static java.util.Collections.singletonList;

public class ElasticsearchRedactPlugin extends Plugin implements ActionPlugin, SearchPlugin {

    @Override
    public List<ActionFilter> getActionFilters() {

        try {

            final Properties properties = new Properties();
            final PhileasConfiguration phileasConfiguration = new PhileasConfiguration(properties);
            final PlainTextFilterService filterService = new PlainTextFilterService(
                    phileasConfiguration, new DefaultContextService(), new InMemoryVectorService(), null);

            return singletonList(new SearchRedactActionFilter(filterService));

        } catch (Exception ex) {
            throw new RuntimeException("Unable to initialize Phileas.", ex);
        }

    }


    @Override
    public List<SearchExtSpec<?>> getSearchExts() {

        final List<SearchExtSpec<?>> searchExts = new ArrayList<>();

        searchExts.add(
                new SearchExtSpec<>(SearchRedactParametersExtBuilder.SEARCH_REDACT_PARAMETERS_NAME, SearchRedactParametersExtBuilder::new, SearchRedactParametersExtBuilder::parse)
        );

        return searchExts;

    }

}
