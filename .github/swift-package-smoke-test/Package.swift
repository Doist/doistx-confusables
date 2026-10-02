// swift-tools-version:5.10
import PackageDescription

let package = Package(
    name: "SwiftPackageSmokeTest",
    platforms: [.iOS(.v15), .watchOS(.v7)],
    products: [
        .library(name: "SwiftPackageSmokeTest", type: .dynamic, targets: ["SwiftPackageSmokeTest"])
    ],
    dependencies: [
        .package(name: "DoistxConfusables", path: "../../assets")
    ],
    targets: [
        .target(
            name: "SwiftPackageSmokeTest",
            dependencies: [.product(name: "DoistxConfusables", package: "DoistxConfusables")]
        )
    ]
)
