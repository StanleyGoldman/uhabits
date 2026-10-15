-- Entries store a state plus a nullable amount instead of one value.
-- Measurable habits (type 1) used to encode skip (3) and unknown (-1) as
-- values; every other value is an amount. A stored 2 on a measurable habit
-- came from old HabitBull imports meaning "1", so it becomes an amount of 1.

begin transaction;

    alter table Repetitions rename to RepetitionsBak;
    create table Repetitions (
        id integer primary key autoincrement,
        habit integer not null references habits(id),
        timestamp integer not null,
        state integer not null,
        amount integer,
        notes text);
    drop index if exists idx_repetitions_habit_timestamp;
    create unique index idx_repetitions_habit_timestamp on Repetitions(
        habit, timestamp);
    insert into Repetitions(id, habit, timestamp, state, amount, notes)
        select r.id, r.habit, r.timestamp,
            case
                when h.type is not 1 then r.value
                when r.value in (3, -1) then r.value
                else 4
            end,
            case
                when h.type is not 1 then null
                when r.value in (3, -1) then null
                when r.value = 2 then 1000
                else r.value
            end,
            r.notes
        from RepetitionsBak r left join Habits h on h.id = r.habit;
    drop table RepetitionsBak;

    alter table Habits add column target_period_mode integer not null default 0;
    alter table Habits add column assume_none integer not null default 1;
    alter table Habits add column skip_reminder_if_logged integer not null default 0;

commit;
