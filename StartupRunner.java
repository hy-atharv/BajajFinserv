@Component
public class StartupRunner implements CommandLineRunner {
    private final UserService userService;

    public StartupRunner(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void run(String... args) {
        userService.processWebhook();
    }
}
