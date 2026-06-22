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

import ai.philterd.phileas.model.filtering.TextFilterResult;
import ai.philterd.phileas.policy.Policy;
import ai.philterd.phileas.services.filters.filtering.PlainTextFilterService;
import ai.philterd.searchredact.ext.SearchRedactParameters;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.ActionFilter;
import org.elasticsearch.action.support.ActionFilterChain;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.tasks.Task;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.Map;

public class SearchRedactActionFilter implements ActionFilter {

    private final PlainTextFilterService filterService;

    public SearchRedactActionFilter(final PlainTextFilterService filterService) {
        this.filterService = filterService;
    }

    @Override
    public int order() {
        return Integer.MAX_VALUE;
    }

    @Override
    public <Request extends ActionRequest, Response extends ActionResponse> void apply(
            Task task,
            String action,
            Request request,
            ActionListener<Response> listener,
            ActionFilterChain<Request, Response> chain
    ) {

        if (!(request instanceof SearchRequest)) {
            chain.proceed(task, action, request, listener);
            return;
        }

        chain.proceed(task, action, request, new ActionListener<>() {

            @Override
            public void onResponse(Response response) {

                if(request instanceof SearchRequest) {

                    try {

                        response = (Response) handleSearchRequest((SearchRequest) request, response);

                    } catch (Exception ex) {
                        throw new RuntimeException("Unable to apply Phileas to search hit.", ex);
                    }

                }

                listener.onResponse(response);

            }

            @Override
            public void onFailure(Exception ex) {
                listener.onFailure(ex);
            }

        });

    }

    private ActionResponse handleSearchRequest(final SearchRequest searchRequest, final ActionResponse response) throws Exception {

        if (response instanceof SearchResponse) {

            final SearchRedactParameters searchRedactParameters = SearchRedactParameters.getSearchRedactParameters(searchRequest);

            if (searchRedactParameters != null) {

                final String policyJson = searchRedactParameters.getPolicy();
                final String context = searchRedactParameters.getContext();
                final String fieldName = searchRedactParameters.getFieldName();
                final String[] fields = fieldName.split(",");

                // LOGGER.info("policy = {}, context = {}, field = {}", policyJson, context, fieldName);

                final ObjectMapper objectMapper = new ObjectMapper();
                final String redactionContext = context != null ? context : "search-redact";

                // Gson uses reflection, so parse the policy inside a privileged block.
                final Policy policy = AccessController.doPrivileged(
                        (PrivilegedAction<Policy>) () -> new Gson().fromJson(policyJson, Policy.class));

                for (final SearchHit hit : ((SearchResponse) response).getHits().getHits()) {

                    // Elasticsearch asserts getSourceAsMap() is called only once per hit, so read it
                    // once, redact every requested field in it, then write it back a single time.
                    final Map<String, Object> sourceMap = hit.getSourceAsMap();
                    boolean modified = false;

                    for (final String field : fields) {

                        if (sourceMap.containsKey(field)) {

                            // Look for PII by applying the policy to the selected field.
                            final String input = sourceMap.get(field).toString();

                            final TextFilterResult filterResult = filterService.filter(policy, redactionContext, input);

                            sourceMap.put(field, filterResult.getFilteredText());
                            modified = true;

                        }
                        // A requested field that is not present in the hit is skipped.

                    }

                    if (modified) {
                        AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
                            try {
                                hit.sourceRef(new BytesArray(objectMapper.writeValueAsBytes(sourceMap)));
                                return null;
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }
                        });
                    }

                }

            }

        }

        return response;

    }

}
