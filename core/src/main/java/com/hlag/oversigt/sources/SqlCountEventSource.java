package com.hlag.oversigt.sources;

import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.hlag.oversigt.core.eventsource.EventSource;
import com.hlag.oversigt.core.eventsource.Property;
import com.hlag.oversigt.sources.event.ComplexGraphEvent;
import com.hlag.oversigt.sources.event.ComplexGraphEvent.Point;

/**
 * @author kayma
 * @deprecated Do not use this event source any longer!
 */
@Deprecated
@EventSource(displayName = "SQL Count", view = "Rickshawgraph", hiddenDataItems = { "moreinfo", "points" })
public class SqlCountEventSource extends AbstractJdbcEventSource<ComplexGraphEvent> {
	private Optional<LocalDate> loadDateYesterdayPoints = Optional.empty();

	private Optional<ZonedDateTime> loadDateTimeTodayPoints = Optional.empty();

	private final List<ComplexGraphEvent.Point> yesterdayPoints = new ArrayList<>();

	private final List<ComplexGraphEvent.Point> todayPoints = new ArrayList<>();

	private long sumToday = 0L;

	private String sqlStatement = "";

	public SqlCountEventSource() {
		// no fields to be initialized
	}

	@Property(name = "SQL",
			description = "SQL Statement returning one row and two columns (hour, count). :DATE: is replaced by the current date/yesterday's date and is mandatory to use. Query has to start with select and no \";\" are allowed.",
			type = "sql")
	public String getSqlStatement() {
		return sqlStatement;
	}

	public void setSqlStatement(final String sqlStatement) {
		// make sure that we have a select statement and nothing else. Some SQL
		// statements starts with "with"
		if (!sqlStatement.startsWith("select") && !sqlStatement.startsWith("with") || sqlStatement.contains(";")) {
			return;
		}

		this.sqlStatement = sqlStatement;
	}

	@Override
	protected void gatherDatabaseInfo(final Connection connection) throws SQLException {
		final LocalDate today = ZonedDateTime.now(ZoneOffset.UTC).toLocalDate();
		final LocalDate yesterday = today.minusDays(1);

		if (yesterdayPoints.isEmpty() || loadDateYesterdayPoints.get().isBefore(yesterday)) {
			yesterdayPoints.clear();
			yesterdayPoints.addAll(getRpMessagesPointsByDate(connection, yesterday));
			loadDateYesterdayPoints = Optional.of(yesterday);
		}

		if (!loadDateTimeTodayPoints.isPresent()
				|| loadDateTimeTodayPoints.get().plusMinutes(15).isBefore(ZonedDateTime.now(ZoneId.of("UTC")))) {
			todayPoints.clear();
			todayPoints.addAll(getRpMessagesPointsByDate(connection, today));
			loadDateTimeTodayPoints = Optional.of(ZonedDateTime.now(ZoneId.of("UTC")));
		}

		sumToday = todayPoints.stream().mapToLong(Point::getY).sum();
	}

	@Override
	protected Optional<ComplexGraphEvent> produceEventFromData() {
		final ComplexGraphEvent.Series yesterdaySeries = new ComplexGraphEvent.Series("Yesterday", yesterdayPoints);
		final ComplexGraphEvent.Series todaySeries = new ComplexGraphEvent.Series("Today", todayPoints);

		final List<ComplexGraphEvent.Series> allSeries = new LinkedList<>();
		allSeries.add(yesterdaySeries);
		allSeries.add(todaySeries);

		final String sumTodayString = NumberFormat.getNumberInstance(Locale.GERMANY).format(sumToday);

		return Optional.of(new ComplexGraphEvent(allSeries, sumTodayString));
	}

	private Collection<Point> getRpMessagesPointsByDate(final Connection con, final LocalDate date)
			throws SQLException {
		return readFromDatabase(con, rs -> {
			final int hour = rs.getInt(1);
			final Long msgs = rs.getLong(2);
			return new ComplexGraphEvent.Point(hour * 60 * 60L, msgs);
		}, sqlStatement.replace(":DATE:", "?"), Date.valueOf(date));
	}
}
