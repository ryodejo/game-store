package gamestore.server;

public final class Store {
    public final UserService users;
    public final GameService games;
    public final CartService cart;
    public final OrderService orders;
    public Store(Database db) {
        users = new UserService(db); games = new GameService(db);
        cart = new CartService(db); orders = new OrderService(db);
    }
}
