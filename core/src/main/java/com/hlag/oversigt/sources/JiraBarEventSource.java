package com.hlag.oversigt.sources;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.hlag.oversigt.core.eventsource.EventSource;
import com.hlag.oversigt.core.eventsource.Property;
import com.hlag.oversigt.properties.Color;
import com.hlag.oversigt.sources.data.DisplayOption;
import com.hlag.oversigt.sources.event.HlBarChartEvent;
import com.hlag.oversigt.sources.event.HlBarChartEvent.Category;
import com.hlag.oversigt.sources.event.HlBarChartEvent.Serie;

@EventSource(view = "HlBarChart", displayName = "Jira Tickets (as Bar)")
public class JiraBarEventSource extends AbstractJiraEventSource<HlBarChartEvent> {
	private int serieMinimum = 3;
	private double minimumHeight = 0.35;

	@Override
	protected HlBarChartEvent produceEvent() {
		Map<DisplayOption, Set<Issue>> issues = getJiraTickets();

		if (issues != null) {
			int maxMailsPerCategory = Math.max(getSerieMinimum(),
					issues.values().stream().mapToInt(Collection::size).max().orElse(0));
			long sumMails = issues.values().stream().flatMap(Set::stream).distinct().count();

			List<Category> categories = new ArrayList<>();
			for (Entry<DisplayOption, Set<Issue>> entry : issues.entrySet()) {
				DisplayOption displayOption = entry.getKey();
				int count = entry.getValue().size();

				Serie serie = getSerie(displayOption.getColor(), count, maxMailsPerCategory);
				categories.add(new Category(Integer.toString(count),
						displayOption.formatDisplayValue(count),
						Arrays.asList(serie)));
			}

			return new HlBarChartEvent(categories, Long.toString(sumMails));
		} else {
			return null;
		}
	}

	private Serie getSerie(Color color, int value, int maximum) {
		double height = 0;
		if (value > 0 && maximum > 1) {
			height = getMinimumHeight() + (double) (value - 1) / (maximum - 1) * (1 - getMinimumHeight());
		}
		return new Serie(color, height);
	}

	@Property(name = "Minimum Height")
	public double getMinimumHeight() {
		return minimumHeight;
	}

	public void setMinimumHeight(double minimumHeight) {
		this.minimumHeight = minimumHeight;
	}

	// TODO this property should be a property of the Widget, not of the EventSource
	@Property(name = "Min Series Value")
	public int getSerieMinimum() {
		return serieMinimum;
	}

	public void setSerieMinimum(int serieMinimum) {
		this.serieMinimum = serieMinimum;
	}
}
