import DoistxConfusables

public func smokeTest() -> Bool {
    ConfusablesKt.isConfusable("paypal", other: "p\u{0430}yp\u{0430}l")
}
