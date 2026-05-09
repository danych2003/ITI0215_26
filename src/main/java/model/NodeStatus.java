package model;

public record NodeStatus(
        String selfAddress,
        int peersCount,
        int blocksCount,
        int transactionsCount
) {
}
